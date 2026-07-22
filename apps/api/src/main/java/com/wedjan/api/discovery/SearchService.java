package com.wedjan.api.discovery;

import com.wedjan.api.common.ApiException;
import com.wedjan.api.config.WedjanProperties;
import com.wedjan.api.discovery.DiscoveryDtos.FacetValue;
import com.wedjan.api.discovery.DiscoveryDtos.PackageSummary;
import com.wedjan.api.discovery.DiscoveryDtos.RelaxationSuggestion;
import com.wedjan.api.discovery.DiscoveryDtos.SearchFacets;
import com.wedjan.api.discovery.DiscoveryDtos.SearchSort;
import com.wedjan.api.discovery.DiscoveryDtos.VendorCard;
import com.wedjan.api.discovery.DiscoveryDtos.VendorSearchResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SearchService {
    private static final int PAGE_SIZE = 24;
    private static final Set<String> BOOKING_MODES = Set.of("INSTANT", "REQUEST");

    private final JdbcTemplate jdbc;
    private final WedjanProperties properties;
    private final SearchAnalyticsService analytics;

    public SearchService(JdbcTemplate jdbc, WedjanProperties properties,
            SearchAnalyticsService analytics) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.analytics = analytics;
    }

    @Transactional(readOnly = true)
    public VendorSearchResponse search(String query, String category, String city,
            Double lat, Double lng, Double radiusKm, Long priceMin, Long priceMax,
            Integer guests, List<String> badges, String bookingMode, LocalDate date, SearchSort sort,
            String cursor, UUID accountId, String sessionId) {
        long started = System.nanoTime();
        String q = clean(query);
        String categorySlug = clean(category);
        String cityName = clean(city);
        List<String> badgeFilters = badges == null ? List.of() : badges.stream()
                .filter(v -> v != null && !v.isBlank()).map(v -> v.toUpperCase(Locale.ROOT))
                .distinct().toList();
        SearchSort effectiveSort = sort == null ? SearchSort.RELEVANCE : sort;
        validate(lat, lng, radiusKm, priceMin, priceMax, guests, bookingMode);

        StringBuilder sql = new StringBuilder("""
                SELECT vp.account_id,vp.slug,vp.business_name,vp.tagline,vp.base_city,vp.base_country,
                    vp.currency,vp.created_at,pc.slug matched_category,p.id package_id,p.title package_title,p.price_cents,
                    p.currency package_currency,p.pricing_model,p.booking_mode,p.deposit_pct,
                    p.cancellation_policy,p.whats_included_md,pm.storage_key package_cover,
                    COALESCE((SELECT array_agg(DISTINCT c2.slug ORDER BY c2.slug)
                        FROM vendor_categories vc2 JOIN categories c2 ON c2.id=vc2.category_id
                        WHERE vc2.vendor_id=vp.account_id AND vc2.deleted_at IS NULL), ARRAY[]::varchar[]) categories,
                    COALESCE((SELECT array_agg(DISTINCT vb.badge ORDER BY vb.badge)
                        FROM vendor_badges vb WHERE vb.vendor_id=vp.account_id AND vb.deleted_at IS NULL
                        AND (vb.expires_at IS NULL OR vb.expires_at>now())), ARRAY[]::varchar[]) badges,
                    COALESCE((SELECT cm.storage_key FROM vendor_media vm
                        JOIN media_assets cm ON cm.id=vm.media_id
                        WHERE vm.vendor_id=vp.account_id AND vm.deleted_at IS NULL
                        ORDER BY CASE vm.kind WHEN 'COVER' THEN 0 ELSE 1 END,vm.sort LIMIT 1),
                        pm.storage_key) cover_storage,
                    CASE WHEN CAST(? AS double precision) IS NULL THEN NULL ELSE (SELECT min(earth_distance(
                        ll_to_earth(?,?),ll_to_earth(sa.lat,sa.lng)))/1000.0
                        FROM service_areas sa WHERE sa.vendor_id=vp.account_id
                        AND sa.deleted_at IS NULL AND sa.lat IS NOT NULL AND sa.lng IS NOT NULL) END distance_km,
                    CASE WHEN CAST(? AS text) IS NULL THEN 0 ELSE
                        ts_rank_cd(vp.search_document,websearch_to_tsquery('simple',?)) +
                        ts_rank_cd(p.search_document,websearch_to_tsquery('simple',?)) +
                        greatest(similarity(vp.business_name,?),similarity(p.title,?),similarity(pc.name,?))
                    END relevance
                FROM vendor_profiles vp
                JOIN packages p ON p.vendor_id=vp.account_id AND p.status='PUBLISHED' AND p.deleted_at IS NULL
                JOIN categories pc ON pc.id=p.category_id AND pc.is_active
                LEFT JOIN categories parent ON parent.id=pc.parent_id
                LEFT JOIN media_assets pm ON pm.id=p.cover_media_id
                WHERE vp.is_public AND vp.status<>'SUSPENDED' AND vp.deleted_at IS NULL
                """);
        List<Object> params = new ArrayList<>();
        params.add(lat); params.add(lat); params.add(lng);
        params.add(q); params.add(q); params.add(q); params.add(q); params.add(q); params.add(q);
        if (q != null) {
            sql.append("""
                     AND (vp.search_document @@ websearch_to_tsquery('simple',?)
                       OR p.search_document @@ websearch_to_tsquery('simple',?)
                       OR similarity(vp.business_name,?)>0.18 OR similarity(p.title,?)>0.18
                       OR similarity(pc.name,?)>0.18 OR similarity(COALESCE(parent.name,''),?)>0.18)
                    """);
            params.addAll(List.of(q, q, q, q, q, q));
        }
        if (categorySlug != null) {
            sql.append(" AND (pc.slug=? OR parent.slug=?)");
            params.add(categorySlug);
            params.add(categorySlug);
        }
        if (cityName != null) {
            sql.append("""
                     AND (lower(vp.base_city)=lower(?) OR EXISTS (SELECT 1 FROM service_areas sc
                         WHERE sc.vendor_id=vp.account_id AND sc.deleted_at IS NULL AND lower(sc.city)=lower(?)))
                    """);
            params.add(cityName);
            params.add(cityName);
        }
        if (lat != null) {
            double requested = radiusKm == null ? 100.0 : radiusKm;
            sql.append("""
                     AND EXISTS (SELECT 1 FROM service_areas sg WHERE sg.vendor_id=vp.account_id
                       AND sg.deleted_at IS NULL AND sg.lat IS NOT NULL AND sg.lng IS NOT NULL
                       AND earth_distance(ll_to_earth(?,?),ll_to_earth(sg.lat,sg.lng)) <= ? * 1000
                       AND earth_distance(ll_to_earth(?,?),ll_to_earth(sg.lat,sg.lng)) <= sg.radius_km * 1000)
                    """);
            params.add(lat); params.add(lng); params.add(requested);
            params.add(lat); params.add(lng);
        }
        if (priceMin != null) { sql.append(" AND p.price_cents>=?"); params.add(priceMin); }
        if (priceMax != null) { sql.append(" AND p.price_cents<=?"); params.add(priceMax); }
        if (guests != null) {
            sql.append(" AND (p.min_guests IS NULL OR p.min_guests<=?) AND (p.max_guests IS NULL OR p.max_guests>=?)");
            params.add(guests); params.add(guests);
        }
        if (bookingMode != null) { sql.append(" AND p.booking_mode=?"); params.add(bookingMode); }
        if (date != null) {
            // Past/same-day checks are intentionally vendor-local; a global JVM date
            // would incorrectly include or exclude vendors across timezone boundaries.
            sql.append("""
                     AND EXISTS (SELECT 1 FROM get_availability(vp.account_id,?,?) ga
                         WHERE ga.status IN ('AVAILABLE','LIMITED'))
                     AND ? >= (current_timestamp AT TIME ZONE vp.timezone)::date
                     AND (? > (current_timestamp AT TIME ZONE vp.timezone)::date OR p.allow_same_day)
                    """);
            params.add(date); params.add(date); params.add(date); params.add(date);
        }
        for (String badge : badgeFilters) {
            sql.append("""
                     AND EXISTS (SELECT 1 FROM vendor_badges bf WHERE bf.vendor_id=vp.account_id
                       AND bf.badge=? AND bf.deleted_at IS NULL AND (bf.expires_at IS NULL OR bf.expires_at>now()))
                    """);
            params.add(badge);
        }

        List<SearchRow> rows = jdbc.query(sql.toString(), this::mapSearchRow, params.toArray());
        List<VendorCard> raw = rows.stream().map(SearchRow::card).toList();
        Map<UUID, VendorCard> cheapestByVendor = new LinkedHashMap<>();
        for (VendorCard card : raw) {
            cheapestByVendor.merge(card.vendorId(), card,
                    (left, right) -> right.cheapestPackage().priceCents()
                            < left.cheapestPackage().priceCents() ? right : left);
        }
        List<VendorCard> all = new ArrayList<>(cheapestByVendor.values());
        all.sort(comparator(effectiveSort));
        SearchFacets facets = facets(rows);
        int offset = cursorOffset(cursor, all);
        int end = Math.min(offset + PAGE_SIZE, all.size());
        List<VendorCard> page = offset >= all.size() ? List.of()
                : withNextAvailableDates(List.copyOf(all.subList(offset, end)));
        String next = end < all.size() && !page.isEmpty() ? encodeCursor(page.getLast().vendorId()) : null;
        List<RelaxationSuggestion> suggestions = all.isEmpty()
                ? relaxations(categorySlug, cityName, priceMin, priceMax) : List.of();

        Map<String, Object> filters = new LinkedHashMap<>();
        put(filters, "category", categorySlug); put(filters, "city", cityName);
        put(filters, "lat", lat); put(filters, "lng", lng); put(filters, "radiusKm", radiusKm);
        put(filters, "priceMin", priceMin); put(filters, "priceMax", priceMax);
        put(filters, "guests", guests); put(filters, "badges", badgeFilters);
        put(filters, "bookingMode", bookingMode); put(filters, "date", date);
        put(filters, "sort", effectiveSort.name());
        long durationMs = (System.nanoTime() - started) / 1_000_000;
        analytics.record(q, filters, all.size(), accountId, clean(sessionId), durationMs);
        return new VendorSearchResponse(page, facets, suggestions, next, all.size());
    }

    @Transactional(readOnly = true)
    public List<VendorCard> cardsForPackages(List<UUID> packageIds) {
        if (packageIds == null || packageIds.isEmpty()) return List.of();
        if (packageIds.size() > 4) throw ApiException.badRequest("COMPARE_LIMIT", "Compare up to 4 packages");
        List<VendorCard> result = new ArrayList<>();
        for (UUID packageId : packageIds) {
            result.add(jdbc.queryForObject("""
                    SELECT vp.account_id,vp.slug,vp.business_name,vp.tagline,vp.base_city,vp.base_country,
                      vp.currency,vp.created_at,p.id package_id,p.title package_title,p.price_cents,
                      p.currency package_currency,p.pricing_model,p.booking_mode,p.deposit_pct,
                      p.cancellation_policy,p.whats_included_md,pm.storage_key package_cover,
                      COALESCE((SELECT array_agg(c.slug ORDER BY c.slug) FROM vendor_categories vc
                        JOIN categories c ON c.id=vc.category_id WHERE vc.vendor_id=vp.account_id
                        AND vc.deleted_at IS NULL),ARRAY[]::varchar[]) categories,
                      COALESCE((SELECT array_agg(vb.badge ORDER BY vb.badge) FROM vendor_badges vb
                        WHERE vb.vendor_id=vp.account_id AND vb.deleted_at IS NULL),ARRAY[]::varchar[]) badges,
                      pm.storage_key cover_storage,NULL::double precision distance_km,0::double precision relevance
                    FROM packages p JOIN vendor_profiles vp ON vp.account_id=p.vendor_id
                    LEFT JOIN media_assets pm ON pm.id=p.cover_media_id
                    WHERE p.id=? AND p.status='PUBLISHED' AND vp.is_public
                    """, this::mapCard, packageId));
        }
        return result;
    }

    private VendorCard mapCard(ResultSet rs, int row) throws SQLException {
        return new VendorCard(rs.getObject("account_id", UUID.class), rs.getString("slug"),
                rs.getString("business_name"), rs.getString("tagline"), rs.getString("base_city"),
                rs.getString("base_country"), rs.getString("currency"), strings(rs.getArray("categories")),
                new PackageSummary(rs.getObject("package_id", UUID.class), rs.getString("package_title"),
                        rs.getLong("price_cents"), rs.getString("package_currency"),
                        rs.getString("pricing_model"), rs.getString("booking_mode"),
                        rs.getInt("deposit_pct"), rs.getString("cancellation_policy"),
                        rs.getString("whats_included_md"), mediaUrl(rs.getString("package_cover"))),
                strings(rs.getArray("badges")), mediaUrl(rs.getString("cover_storage")),
                nullableDouble(rs, "distance_km"), List.of(), rs.getDouble("relevance"));
    }

    private SearchRow mapSearchRow(ResultSet rs, int row) throws SQLException {
        return new SearchRow(mapCard(rs, row), rs.getString("matched_category"));
    }

    private Comparator<VendorCard> comparator(SearchSort sort) {
        Comparator<VendorCard> byId = Comparator.comparing(v -> v.vendorId().toString());
        return switch (sort) {
            case PRICE_ASC -> Comparator.comparingLong((VendorCard v) -> v.cheapestPackage().priceCents()).thenComparing(byId);
            case PRICE_DESC -> Comparator.comparingLong((VendorCard v) -> v.cheapestPackage().priceCents()).reversed().thenComparing(byId);
            // All domain ids are UUIDv7, whose canonical string is time ordered.
            case NEWEST -> byId.reversed();
            case RATING -> byId; // merit/rating is intentionally filled by Phase 7.
            case RELEVANCE -> Comparator.comparingDouble((VendorCard v) -> v.relevance() == null ? 0 : v.relevance()).reversed()
                    .thenComparing(byId);
        };
    }

    private SearchFacets facets(List<SearchRow> rows) {
        Map<String, Set<UUID>> categories = new HashMap<>();
        Map<String, Set<UUID>> cities = new HashMap<>();
        Map<String, Set<UUID>> badges = new HashMap<>();
        Map<String, Set<UUID>> modes = new HashMap<>();
        rows.forEach(row -> {
            VendorCard card = row.card();
            facet(categories, row.matchedCategory(), card.vendorId());
            facet(cities, card.city(), card.vendorId());
            card.badges().forEach(value -> facet(badges, value, card.vendorId()));
            facet(modes, card.cheapestPackage().bookingMode(), card.vendorId());
        });
        return new SearchFacets(values(categories), values(cities), values(badges), values(modes));
    }

    private List<RelaxationSuggestion> relaxations(String category, String city, Long min, Long max) {
        List<RelaxationSuggestion> result = new ArrayList<>();
        if (city != null) {
            jdbc.query("""
                    SELECT DISTINCT vp.base_city FROM vendor_profiles vp JOIN packages p ON p.vendor_id=vp.account_id
                    WHERE vp.is_public AND lower(vp.base_city)<>lower(?) AND p.status='PUBLISHED'
                    ORDER BY vp.base_city LIMIT 3
                    """, (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                            result.add(new RelaxationSuggestion("NEARBY_CITY",
                                    "Try vendors in " + rs.getString(1), rs.getString(1))), city);
        }
        if (category != null) {
            jdbc.query("""
                    SELECT DISTINCT sibling.slug,sibling.name FROM categories chosen
                    JOIN categories sibling ON sibling.parent_id IS NOT DISTINCT FROM chosen.parent_id
                    WHERE chosen.slug=? AND sibling.slug<>chosen.slug AND sibling.is_active
                    ORDER BY sibling.name LIMIT 3
                    """, (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                            result.add(new RelaxationSuggestion("ADJACENT_CATEGORY",
                                    "Explore " + rs.getString(2), rs.getString(1))), category);
        }
        if (min != null || max != null) {
            result.add(new RelaxationSuggestion("WIDEN_PRICE", "Widen your price range by 25%", "25"));
        }
        if (result.isEmpty()) result.add(new RelaxationSuggestion("CLEAR_FILTERS", "Clear filters to see all vendors", "all"));
        return List.copyOf(result);
    }

    private static void validate(Double lat, Double lng, Double radius, Long min, Long max,
            Integer guests, String bookingMode) {
        if ((lat == null) != (lng == null)) throw ApiException.badRequest("SEARCH_LOCATION_INVALID", "Latitude and longitude are required together");
        if (lat != null && (lat < -90 || lat > 90 || lng < -180 || lng > 180)) throw ApiException.badRequest("SEARCH_LOCATION_INVALID", "Invalid coordinates");
        if (radius != null && (radius <= 0 || radius > 1000)) throw ApiException.badRequest("SEARCH_RADIUS_INVALID", "Radius must be between 0 and 1000 km");
        if (min != null && min < 0 || max != null && max < 0 || min != null && max != null && min > max) throw ApiException.badRequest("SEARCH_PRICE_INVALID", "Invalid price range");
        if (guests != null && guests < 1) throw ApiException.badRequest("SEARCH_GUESTS_INVALID", "Guests must be positive");
        if (bookingMode != null && !BOOKING_MODES.contains(bookingMode)) throw ApiException.badRequest("SEARCH_BOOKING_MODE_INVALID", "Booking mode must be INSTANT or REQUEST");
    }

    private List<VendorCard> withNextAvailableDates(List<VendorCard> cards) {
        if (cards.isEmpty()) return cards;
        String values = String.join(",", java.util.Collections.nCopies(cards.size(), "(?::uuid)"));
        List<Object> ids = cards.stream().map(VendorCard::vendorId).map(id -> (Object) id).toList();
        Map<UUID, List<LocalDate>> dates = new HashMap<>();
        jdbc.query("""
                WITH requested(vendor_id) AS (VALUES %s)
                SELECT requested.vendor_id, available.date
                FROM requested
                JOIN vendor_profiles vp ON vp.account_id=requested.vendor_id
                CROSS JOIN LATERAL (
                    SELECT date FROM get_availability(requested.vendor_id,
                        (current_timestamp AT TIME ZONE vp.timezone)::date+1,
                        (current_timestamp AT TIME ZONE vp.timezone)::date+90)
                    WHERE status IN ('AVAILABLE','LIMITED') ORDER BY date LIMIT 3
                ) available ORDER BY requested.vendor_id,available.date
                """.formatted(values), (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                dates.computeIfAbsent(rs.getObject("vendor_id", UUID.class), ignored -> new ArrayList<>())
                        .add(rs.getObject("date", LocalDate.class)), ids.toArray());
        return cards.stream().map(card -> new VendorCard(card.vendorId(), card.slug(), card.businessName(),
                card.tagline(), card.city(), card.country(), card.currency(), card.categories(),
                card.cheapestPackage(), card.badges(), card.coverUrl(), card.distanceKm(),
                List.copyOf(dates.getOrDefault(card.vendorId(), List.of())), card.relevance())).toList();
    }

    private static void facet(Map<String, Set<UUID>> target, String value, UUID vendorId) {
        if (value != null) target.computeIfAbsent(value, ignored -> new java.util.HashSet<>()).add(vendorId);
    }
    private static List<FacetValue> values(Map<String, Set<UUID>> source) {
        return source.entrySet().stream().filter(e -> e.getKey() != null)
                .sorted(Comparator.<Map.Entry<String, Set<UUID>>>comparingInt(e -> e.getValue().size())
                        .reversed().thenComparing(Map.Entry::getKey))
                .map(e -> new FacetValue(e.getKey(), e.getValue().size())).toList();
    }
    private static List<String> strings(Array array) throws SQLException {
        if (array == null) return List.of();
        return List.of((String[]) array.getArray());
    }
    private String mediaUrl(String key) { return key == null ? null : properties.media().publicBaseUrl() + "/" + key; }
    private static Double nullableDouble(ResultSet rs, String name) throws SQLException { double v=rs.getDouble(name); return rs.wasNull()?null:v; }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static void put(Map<String,Object> map, String key, Object value) { if (value != null && (!(value instanceof List<?> l) || !l.isEmpty())) map.put(key,value); }
    private static String encodeCursor(UUID lastVendorId) { return Base64.getUrlEncoder().withoutPadding().encodeToString(("v2:"+lastVendorId).getBytes(StandardCharsets.UTF_8)); }
    private static int cursorOffset(String cursor, List<VendorCard> sorted) {
        if (cursor == null || cursor.isBlank()) return 0;
        try {
            String value = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            if (!value.startsWith("v2:")) throw new IllegalArgumentException();
            UUID last = UUID.fromString(value.substring(3));
            for (int i=0;i<sorted.size();i++) if (sorted.get(i).vendorId().equals(last)) return i+1;
            throw new IllegalArgumentException();
        } catch (RuntimeException ex) { throw ApiException.badRequest("SEARCH_CURSOR_INVALID", "Invalid search cursor"); }
    }
    private record SearchRow(VendorCard card, String matchedCategory) {}
}
