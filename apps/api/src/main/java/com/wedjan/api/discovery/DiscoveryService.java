package com.wedjan.api.discovery;

import com.wedjan.api.auth.MailService;
import com.wedjan.api.common.ApiException;
import com.wedjan.api.common.Uuidv7;
import com.wedjan.api.config.WedjanProperties;
import com.wedjan.api.discovery.DiscoveryDtos.CompareResponse;
import com.wedjan.api.discovery.DiscoveryDtos.EntityType;
import com.wedjan.api.discovery.DiscoveryDtos.Favorite;
import com.wedjan.api.discovery.DiscoveryDtos.FavoriteListResponse;
import com.wedjan.api.discovery.DiscoveryDtos.FavoriteRequest;
import com.wedjan.api.discovery.DiscoveryDtos.LandingPage;
import com.wedjan.api.discovery.DiscoveryDtos.LandingRoute;
import com.wedjan.api.discovery.DiscoveryDtos.LandingRouteList;
import com.wedjan.api.discovery.DiscoveryDtos.SeoFaq;
import com.wedjan.api.discovery.DiscoveryDtos.Shortlist;
import com.wedjan.api.discovery.DiscoveryDtos.ShortlistItem;
import com.wedjan.api.discovery.DiscoveryDtos.ShortlistItemRequest;
import com.wedjan.api.discovery.DiscoveryDtos.ShortlistListResponse;
import com.wedjan.api.discovery.DiscoveryDtos.ShortlistRequest;
import com.wedjan.api.discovery.DiscoveryDtos.Showcase;
import com.wedjan.api.discovery.DiscoveryDtos.ShowcaseFeedResponse;
import com.wedjan.api.discovery.DiscoveryDtos.ShowcaseMedia;
import com.wedjan.api.discovery.DiscoveryDtos.ShowcaseRequest;
import com.wedjan.api.discovery.DiscoveryDtos.ShowcaseStatus;
import com.wedjan.api.discovery.DiscoveryDtos.ShowcaseVendorTag;
import com.wedjan.api.discovery.DiscoveryDtos.TagStatus;
import com.wedjan.api.discovery.DiscoveryDtos.VendorCard;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiscoveryService {
    private static final int FEED_SIZE = 20;
    private final JdbcTemplate jdbc;
    private final WedjanProperties properties;
    private final MailService mailService;
    private final SearchService searchService;

    public DiscoveryService(JdbcTemplate jdbc, WedjanProperties properties,
            MailService mailService, SearchService searchService) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.mailService = mailService;
        this.searchService = searchService;
    }

    @Transactional
    public Showcase createShowcase(UUID accountId, ShowcaseRequest request) {
        requireVendor(accountId);
        validateShowcase(accountId, request);
        UUID id = Uuidv7.next();
        String slug = uniqueShowcaseSlug(request.title());
        jdbc.update("""
                INSERT INTO showcases(id,vendor_id,title,slug,event_type,event_date,city,country,
                  cover_media_id,description_md,status,style_tags,created_by)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, accountId, request.title().trim(), slug, request.eventType().name(),
                request.eventDate(), request.city().trim(), request.country().trim().toUpperCase(Locale.ROOT),
                request.coverMediaId(), clean(request.descriptionMd()),
                value(request.status(), ShowcaseStatus.DRAFT).name(), sqlArray(request.styleTags()), accountId);
        replaceShowcaseChildren(id, accountId, request);
        return showcase(id, accountId, false);
    }

    @Transactional
    public Showcase updateShowcase(UUID accountId, UUID showcaseId, ShowcaseRequest request) {
        requireOwnedShowcase(accountId, showcaseId);
        validateShowcase(accountId, request);
        jdbc.update("""
                UPDATE showcases SET title=?,event_type=?,event_date=?,city=?,country=?,cover_media_id=?,
                  description_md=?,status=?,style_tags=?,updated_at=now()
                WHERE id=? AND vendor_id=? AND deleted_at IS NULL
                """, request.title().trim(), request.eventType().name(), request.eventDate(),
                request.city().trim(), request.country().trim().toUpperCase(Locale.ROOT),
                request.coverMediaId(), clean(request.descriptionMd()),
                value(request.status(), ShowcaseStatus.DRAFT).name(), sqlArray(request.styleTags()),
                showcaseId, accountId);
        replaceShowcaseChildren(showcaseId, accountId, request);
        return showcase(showcaseId, accountId, false);
    }

    @Transactional
    public void deleteShowcase(UUID accountId, UUID showcaseId) {
        if (jdbc.update("UPDATE showcases SET deleted_at=now(),updated_at=now() WHERE id=? AND vendor_id=? AND deleted_at IS NULL",
                showcaseId, accountId) == 0) throw notFound("Showcase");
    }

    @Transactional(readOnly = true)
    public ShowcaseFeedResponse myShowcases(UUID accountId) {
        List<UUID> ids = jdbc.query("SELECT id FROM showcases WHERE vendor_id=? AND deleted_at IS NULL ORDER BY created_at DESC",
                (rs, n) -> rs.getObject(1, UUID.class), accountId);
        return new ShowcaseFeedResponse(ids.stream().map(id -> showcase(id, accountId, false)).toList(), null);
    }

    @Transactional(readOnly = true)
    public ShowcaseFeedResponse pendingTags(UUID accountId) {
        List<UUID> ids = jdbc.query("""
                SELECT s.id FROM showcase_vendor_tags svt JOIN showcases s ON s.id=svt.showcase_id
                WHERE svt.vendor_id=? AND svt.status='PENDING' AND s.deleted_at IS NULL
                ORDER BY svt.created_at DESC
                """, (rs, n) -> rs.getObject(1, UUID.class), accountId);
        return new ShowcaseFeedResponse(ids.stream().map(id -> showcase(id, accountId, false)).toList(), null);
    }

    @Transactional
    public Showcase respondToTag(UUID accountId, UUID tagId, boolean accept) {
        UUID showcaseId;
        try {
            showcaseId = jdbc.queryForObject("""
                    UPDATE showcase_vendor_tags SET status=?,responded_at=now(),updated_at=now()
                    WHERE id=? AND vendor_id=? AND status='PENDING' RETURNING showcase_id
                    """, UUID.class, accept ? "ACCEPTED" : "DECLINED", tagId, accountId);
        } catch (EmptyResultDataAccessException ex) {
            throw ApiException.conflict("SHOWCASE_TAG_NOT_PENDING", "This tag is not awaiting your response");
        }
        return showcase(showcaseId, accountId, false);
    }

    @Transactional(readOnly = true)
    public ShowcaseFeedResponse feed(String eventType, String city, List<String> styleTags,
            String cursor, UUID accountId) {
        int offset = decodeCursor(cursor);
        StringBuilder sql = new StringBuilder("SELECT id FROM showcases WHERE status='PUBLISHED' AND deleted_at IS NULL");
        List<Object> params = new ArrayList<>();
        if (clean(eventType) != null) { sql.append(" AND event_type=?"); params.add(eventType.toUpperCase(Locale.ROOT)); }
        if (clean(city) != null) { sql.append(" AND lower(city)=lower(?)"); params.add(city.trim()); }
        if (styleTags != null && !styleTags.isEmpty()) {
            sql.append(" AND style_tags && ?"); params.add(sqlArray(styleTags));
        }
        sql.append(" ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?");
        params.add(FEED_SIZE + 1); params.add(offset);
        List<UUID> ids = jdbc.query(sql.toString(), (rs, n) -> rs.getObject(1, UUID.class), params.toArray());
        boolean more = ids.size() > FEED_SIZE;
        if (more) ids = ids.subList(0, FEED_SIZE);
        List<Showcase> items = ids.stream().map(id -> showcase(id, accountId, true)).toList();
        return new ShowcaseFeedResponse(items, more ? encodeCursor(offset + FEED_SIZE) : null);
    }

    @Transactional(readOnly = true)
    public Showcase publicShowcase(String idOrSlug, UUID accountId) {
        UUID id;
        try { id = UUID.fromString(idOrSlug); }
        catch (IllegalArgumentException ex) {
            try { id = jdbc.queryForObject("SELECT id FROM showcases WHERE slug=? AND status='PUBLISHED' AND deleted_at IS NULL", UUID.class, idOrSlug); }
            catch (EmptyResultDataAccessException missing) { throw notFound("Showcase"); }
        }
        return showcase(id, accountId, true);
    }

    @Transactional
    public Favorite favorite(UUID accountId, FavoriteRequest request) {
        requireEntity(request.entityType(), request.entityId());
        jdbc.update("""
                INSERT INTO favorites(account_id,entity_type,entity_id) VALUES (?,?,?)
                ON CONFLICT DO NOTHING
                """, accountId, request.entityType().name(), request.entityId());
        return jdbc.queryForObject("SELECT entity_type,entity_id,created_at FROM favorites WHERE account_id=? AND entity_type=? AND entity_id=?",
                this::mapFavorite, accountId, request.entityType().name(), request.entityId());
    }

    @Transactional
    public void unfavorite(UUID accountId, EntityType type, UUID entityId) {
        jdbc.update("DELETE FROM favorites WHERE account_id=? AND entity_type=? AND entity_id=?", accountId, type.name(), entityId);
    }

    @Transactional(readOnly = true)
    public FavoriteListResponse favorites(UUID accountId) {
        return new FavoriteListResponse(jdbc.query("""
                SELECT entity_type,entity_id,created_at FROM favorites WHERE account_id=? ORDER BY created_at DESC
                """, this::mapFavorite, accountId));
    }

    @Transactional
    public Shortlist createShortlist(UUID accountId, ShortlistRequest request) {
        UUID id = Uuidv7.next();
        try {
            jdbc.update("INSERT INTO shortlists(id,account_id,name) VALUES (?,?,?)", id, accountId, request.name().trim());
        } catch (RuntimeException ex) {
            throw ApiException.conflict("SHORTLIST_NAME_EXISTS", "A shortlist with that name already exists");
        }
        return shortlist(accountId, id);
    }

    @Transactional
    public Shortlist addShortlistItem(UUID accountId, UUID shortlistId, ShortlistItemRequest request) {
        requireShortlist(accountId, shortlistId);
        if (request.packageId() != null && jdbc.queryForObject("SELECT count(*) FROM packages WHERE id=? AND vendor_id=? AND status='PUBLISHED'", Long.class,
                request.packageId(), request.vendorId()) == 0) {
            throw ApiException.badRequest("SHORTLIST_PACKAGE_INVALID", "Package does not belong to this vendor");
        }
        if (jdbc.queryForObject("SELECT count(*) FROM vendor_profiles WHERE account_id=? AND is_public", Long.class, request.vendorId()) == 0) throw notFound("Vendor");
        jdbc.update("""
                INSERT INTO shortlist_items(id,shortlist_id,vendor_id,package_id,note) VALUES (?,?,?,?,?)
                ON CONFLICT (shortlist_id,vendor_id,package_id)
                DO UPDATE SET note=excluded.note
                """, Uuidv7.next(), shortlistId, request.vendorId(), request.packageId(), clean(request.note()));
        return shortlist(accountId, shortlistId);
    }

    @Transactional
    public void deleteShortlistItem(UUID accountId, UUID shortlistId, UUID itemId) {
        requireShortlist(accountId, shortlistId);
        if (jdbc.update("DELETE FROM shortlist_items WHERE id=? AND shortlist_id=?", itemId, shortlistId) == 0) throw notFound("Shortlist item");
    }

    @Transactional(readOnly = true)
    public ShortlistListResponse shortlists(UUID accountId) {
        List<UUID> ids = jdbc.query("SELECT id FROM shortlists WHERE account_id=? ORDER BY updated_at DESC",
                (rs,n)->rs.getObject(1,UUID.class), accountId);
        return new ShortlistListResponse(ids.stream().map(id -> shortlist(accountId,id)).toList());
    }

    @Transactional(readOnly = true)
    public CompareResponse compare(UUID accountId, List<UUID> packageIds, String displayCurrency) {
        List<VendorCard> cards = searchService.cardsForPackages(packageIds);
        String currency = clean(displayCurrency);
        if (currency == null && accountId != null) currency = jdbc.queryForObject("SELECT default_currency FROM accounts WHERE id=?", String.class, accountId);
        if (currency == null) currency = "AUD";
        currency = currency.toUpperCase(Locale.ROOT);
        Map<UUID, Long> approximate = new HashMap<>();
        for (VendorCard card : cards) {
            var pack = card.cheapestPackage();
            if (pack.currency().equals(currency)) approximate.put(pack.id(), pack.priceCents());
            else {
                Double rate = jdbc.query("""
                        SELECT rate::double precision FROM fx_rates WHERE base_currency=? AND quote_currency=?
                        ORDER BY rate_date DESC LIMIT 1
                        """, rs -> rs.next() ? rs.getDouble(1) : null, pack.currency(), currency);
                if (rate != null) approximate.put(pack.id(), Math.round(pack.priceCents() * rate));
            }
        }
        return new CompareResponse(cards, currency, approximate,
                "Approximate conversions use the latest daily static FX rate; checkout always uses the package's native currency.");
    }

    @Transactional(readOnly = true)
    public LandingRouteList landingRoutes() {
        return new LandingRouteList(jdbc.query("""
                SELECT DISTINCT lower(vp.base_country),lower(regexp_replace(vp.base_city,'[^a-zA-Z0-9]+','-','g')),c.slug
                FROM vendor_profiles vp JOIN vendor_categories vc ON vc.vendor_id=vp.account_id
                JOIN categories c ON c.id=vc.category_id JOIN packages p ON p.vendor_id=vp.account_id
                WHERE vp.is_public AND vp.deleted_at IS NULL AND p.status='PUBLISHED' AND c.is_active
                ORDER BY 1,2,3
                """, (rs,n)->new LandingRoute(rs.getString(1),rs.getString(2),rs.getString(3))));
    }

    @Transactional(readOnly = true)
    public LandingPage landing(String country, String citySlug, String categorySlug) {
        String city = citySlug.replace('-', ' ');
        String categoryName;
        try { categoryName = jdbc.queryForObject("SELECT name FROM categories WHERE slug=? AND is_active", String.class, categorySlug); }
        catch (EmptyResultDataAccessException ex) { throw notFound("Category landing page"); }
        List<VendorCard> vendors = searchService.search(null, categorySlug, city, null, null, null,
                null, null, null, List.of(), null, DiscoveryDtos.SearchSort.RELEVANCE,
                null, null, "seo").items().stream()
                .filter(v -> v.country().equalsIgnoreCase(country)).toList();
        LandingStats stats = jdbc.queryForObject("""
                SELECT count(DISTINCT vp.account_id),min(p.price_cents),max(p.price_cents),
                  percentile_disc(0.5) WITHIN GROUP (ORDER BY p.price_cents),min(p.currency),min(vp.base_city)
                FROM vendor_profiles vp JOIN packages p ON p.vendor_id=vp.account_id
                JOIN categories c ON c.id=p.category_id LEFT JOIN categories parent ON parent.id=c.parent_id
                WHERE vp.is_public AND vp.status<>'SUSPENDED' AND vp.deleted_at IS NULL
                  AND p.status='PUBLISHED' AND p.deleted_at IS NULL
                  AND lower(vp.base_country)=lower(?)
                  AND lower(regexp_replace(vp.base_city,'[^a-zA-Z0-9]+','-','g'))=?
                  AND (c.slug=? OR parent.slug=?)
                """, (rs,n)->new LandingStats(rs.getLong(1),nullableLong(rs,2),nullableLong(rs,3),
                        nullableLong(rs,4),rs.getString(5),rs.getString(6)), country, citySlug,
                        categorySlug, categorySlug);
        if (stats == null || stats.vendorCount()==0 || vendors.isEmpty()) throw notFound("Category landing page");
        long min = stats.minimum(), max = stats.maximum(), median = stats.median();
        String currency = stats.currency();
        String displayCity = stats.city();
        List<SeoFaq> faqs = jdbc.query("""
                SELECT question_tpl,answer_tpl FROM faq_templates ft JOIN categories c ON c.id=ft.category_id
                WHERE c.slug=? ORDER BY ft.sort
                """, (rs,n) -> new SeoFaq(tokens(rs.getString(1),categoryName,displayCity,min,median,currency),
                        tokens(rs.getString(2),categoryName,displayCity,min,median,currency)), categorySlug);
        List<String> siblingCities = jdbc.query("""
                SELECT DISTINCT lower(regexp_replace(vp.base_city,'[^a-zA-Z0-9]+','-','g'))
                FROM vendor_profiles vp JOIN vendor_categories vc ON vc.vendor_id=vp.account_id
                JOIN categories c ON c.id=vc.category_id WHERE vp.is_public AND c.slug=?
                ORDER BY 1
                """, (rs,n)->rs.getString(1), categorySlug);
        List<String> siblingCategories = jdbc.query("""
                SELECT DISTINCT c.slug FROM categories c JOIN vendor_categories vc ON vc.category_id=c.id
                JOIN vendor_profiles vp ON vp.account_id=vc.vendor_id
                WHERE vp.is_public AND lower(vp.base_country)=lower(?) AND lower(regexp_replace(vp.base_city,'[^a-zA-Z0-9]+','-','g'))=?
                ORDER BY c.slug
                """, (rs,n)->rs.getString(1), country, citySlug);
        return new LandingPage(country.toLowerCase(Locale.ROOT), displayCity, categorySlug,
                categoryName, stats.vendorCount(), min, max, median, currency, vendors, faqs,
                siblingCities, siblingCategories);
    }

    private void replaceShowcaseChildren(UUID showcaseId, UUID owner, ShowcaseRequest request) {
        jdbc.update("DELETE FROM showcase_media WHERE showcase_id=?", showcaseId);
        int sort = 0;
        for (var item : request.media()) {
            requireOwnedMedia(owner, item.mediaId());
            jdbc.update("INSERT INTO showcase_media(id,showcase_id,media_id,caption,sort) VALUES (?,?,?,?,?)",
                    Uuidv7.next(), showcaseId, item.mediaId(), clean(item.caption()), value(item.sort(), sort++));
        }
        List<UUID> requested = request.vendorTags() == null ? List.of() : request.vendorTags().stream().map(DiscoveryDtos.ShowcaseVendorTagRequest::vendorId).toList();
        if (requested.contains(owner) || requested.stream().distinct().count()!=requested.size()) throw ApiException.badRequest("SHOWCASE_TAGS_INVALID", "Do not tag your own business or duplicate a vendor");
        jdbc.update("DELETE FROM showcase_vendor_tags WHERE showcase_id=? AND status='PENDING'", showcaseId);
        for (var tag : request.vendorTags() == null ? List.<DiscoveryDtos.ShowcaseVendorTagRequest>of() : request.vendorTags()) {
            String[] vendor = requireVendor(tag.vendorId());
            UUID tagId = Uuidv7.next();
            int inserted = jdbc.update("""
                    INSERT INTO showcase_vendor_tags(id,showcase_id,vendor_id,role_label)
                    VALUES (?,?,?,?) ON CONFLICT (showcase_id,vendor_id) DO NOTHING
                    """, tagId, showcaseId, tag.vendorId(), tag.roleLabel().trim());
            if (inserted > 0) {
                String ownerName = requireVendor(owner)[0];
                mailService.sendShowcaseTagRequest(vendor[1], request.title().trim(), ownerName,
                        properties.publicWebUrl()+"/dashboard?showcaseTags=pending");
            }
        }
    }

    private Showcase showcase(UUID id, UUID viewer, boolean publicOnly) {
        try {
            Showcase base = jdbc.queryForObject("""
                    SELECT s.id,s.vendor_id,vp.slug owner_slug,vp.business_name owner_name,s.title,s.slug,
                      s.event_type,s.event_date,s.city,s.country,s.description_md,s.status,s.style_tags,
                      ma.storage_key cover_storage,s.created_at
                    FROM showcases s JOIN vendor_profiles vp ON vp.account_id=s.vendor_id
                    LEFT JOIN media_assets ma ON ma.id=s.cover_media_id
                    WHERE s.id=? AND s.deleted_at IS NULL
                    """, (rs,n)->mapShowcaseBase(rs,viewer), id);
            if (publicOnly && base.status()!=ShowcaseStatus.PUBLISHED) throw notFound("Showcase");
            boolean ownerView = viewer != null && viewer.equals(base.ownerVendorId());
            List<ShowcaseMedia> media = jdbc.query("""
                    SELECT sm.id,sm.media_id,sm.caption,sm.sort,ma.storage_key,ma.width,ma.height,ma.blurhash
                    FROM showcase_media sm JOIN media_assets ma ON ma.id=sm.media_id
                    WHERE sm.showcase_id=? ORDER BY sm.sort,sm.id
                    """, this::mapShowcaseMedia,id);
            String tagWhere = publicOnly ? " AND svt.status='ACCEPTED'"
                    : ownerView ? "" : " AND (svt.status='ACCEPTED' OR svt.vendor_id=?)";
            List<Object> tagParams = new ArrayList<>(); tagParams.add(id);
            if (!publicOnly && !ownerView) tagParams.add(viewer);
            List<ShowcaseVendorTag> tags = jdbc.query("""
                    SELECT svt.id,svt.vendor_id,vp.slug,vp.business_name,svt.role_label,svt.status
                    FROM showcase_vendor_tags svt JOIN vendor_profiles vp ON vp.account_id=svt.vendor_id
                    WHERE svt.showcase_id=?"""+tagWhere+" ORDER BY svt.created_at", this::mapTag,
                    tagParams.toArray());
            return new Showcase(base.id(),base.ownerVendorId(),base.ownerVendorSlug(),base.ownerBusinessName(),
                    base.title(),base.slug(),base.eventType(),base.eventDate(),base.city(),base.country(),
                    base.descriptionMd(),base.status(),base.styleTags(),base.coverUrl(),media,tags,
                    base.createdAt(),base.favorite());
        } catch (EmptyResultDataAccessException ex) { throw notFound("Showcase"); }
    }

    private Showcase mapShowcaseBase(ResultSet rs, UUID viewer) throws SQLException {
        UUID id=rs.getObject("id",UUID.class);
        boolean favorite=viewer!=null && jdbc.queryForObject("SELECT count(*) FROM favorites WHERE account_id=? AND entity_type='SHOWCASE' AND entity_id=?",Long.class,viewer,id)>0;
        return new Showcase(id,rs.getObject("vendor_id",UUID.class),rs.getString("owner_slug"),rs.getString("owner_name"),
                rs.getString("title"),rs.getString("slug"),DiscoveryDtos.EventType.valueOf(rs.getString("event_type")),
                rs.getObject("event_date",LocalDate.class),rs.getString("city"),rs.getString("country"),
                rs.getString("description_md"),ShowcaseStatus.valueOf(rs.getString("status")),strings(rs.getArray("style_tags")),
                mediaUrl(rs.getString("cover_storage")),List.of(),List.of(),instant(rs,"created_at"),favorite);
    }
    private ShowcaseMedia mapShowcaseMedia(ResultSet rs,int n)throws SQLException{return new ShowcaseMedia(rs.getObject("id",UUID.class),rs.getObject("media_id",UUID.class),rs.getString("caption"),rs.getInt("sort"),mediaUrl(rs.getString("storage_key")),integer(rs,"width"),integer(rs,"height"),rs.getString("blurhash"));}
    private ShowcaseVendorTag mapTag(ResultSet rs,int n)throws SQLException{return new ShowcaseVendorTag(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),rs.getString(4),rs.getString(5),TagStatus.valueOf(rs.getString(6)));}
    private Favorite mapFavorite(ResultSet rs,int n)throws SQLException{return new Favorite(EntityType.valueOf(rs.getString(1)),rs.getObject(2,UUID.class),instant(rs,3));}

    private Shortlist shortlist(UUID accountId,UUID id){
        requireShortlist(accountId,id);
        return jdbc.queryForObject("SELECT id,name,created_at FROM shortlists WHERE id=?",(rs,n)->new Shortlist(rs.getObject(1,UUID.class),rs.getString(2),shortlistItems(id),instant(rs,3)),id);
    }
    private List<ShortlistItem> shortlistItems(UUID id){return jdbc.query("""
            SELECT si.id,si.vendor_id,vp.slug,vp.business_name,si.package_id,p.title,p.price_cents,p.currency,si.note
            FROM shortlist_items si JOIN vendor_profiles vp ON vp.account_id=si.vendor_id
            LEFT JOIN packages p ON p.id=si.package_id WHERE si.shortlist_id=? ORDER BY si.created_at
            """,(rs,n)->new ShortlistItem(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),rs.getString(4),rs.getObject(5,UUID.class),rs.getString(6),nullableLong(rs,7),rs.getString(8),rs.getString(9)),id);}

    private void validateShowcase(UUID owner,ShowcaseRequest request){requireOwnedMedia(owner,request.coverMediaId());if(request.status()==ShowcaseStatus.PUBLISHED && request.media().size()<3)throw ApiException.badRequest("SHOWCASE_PUBLISH_GATES","Published showcases need at least 3 photos");}
    private void requireOwnedMedia(UUID owner,UUID mediaId){Long count=jdbc.queryForObject("SELECT count(*) FROM media_assets WHERE id=? AND owner_account_id=? AND status='READY' AND kind='IMAGE'",Long.class,mediaId,owner);if(count==0)throw ApiException.badRequest("SHOWCASE_MEDIA_INVALID","Use your own completed image upload");}
    private String[] requireVendor(UUID id){try{return jdbc.queryForObject("SELECT vp.business_name,a.email FROM vendor_profiles vp JOIN accounts a ON a.id=vp.account_id WHERE vp.account_id=? AND vp.deleted_at IS NULL",(rs,n)->new String[]{rs.getString(1),rs.getString(2)},id);}catch(EmptyResultDataAccessException ex){throw notFound("Vendor");}}
    private void requireOwnedShowcase(UUID owner,UUID id){if(jdbc.queryForObject("SELECT count(*) FROM showcases WHERE id=? AND vendor_id=? AND deleted_at IS NULL",Long.class,id,owner)==0)throw notFound("Showcase");}
    private void requireShortlist(UUID accountId,UUID id){if(jdbc.queryForObject("SELECT count(*) FROM shortlists WHERE id=? AND account_id=?",Long.class,id,accountId)==0)throw notFound("Shortlist");}
    private void requireEntity(EntityType type,UUID id){String sql=switch(type){case VENDOR->"SELECT count(*) FROM vendor_profiles WHERE account_id=? AND is_public";case SHOWCASE->"SELECT count(*) FROM showcases WHERE id=? AND status='PUBLISHED' AND deleted_at IS NULL";case PACKAGE->"SELECT count(*) FROM packages WHERE id=? AND status='PUBLISHED' AND deleted_at IS NULL";};if(jdbc.queryForObject(sql,Long.class,id)==0)throw notFound(type.name().toLowerCase(Locale.ROOT));}
    private String uniqueShowcaseSlug(String title){String base=slugify(title),value=base;int n=2;while(jdbc.queryForObject("SELECT count(*) FROM showcases WHERE slug=?",Long.class,value)>0)value=base+"-"+n++;return value;}
    private static String slugify(String value){String slug=value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","-").replaceAll("(^-|-$)","");return slug.isBlank()?"event":slug;}
    private Array sqlArray(List<String> value){return jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Array>) c->c.createArrayOf("text",value==null?new String[0]:value.toArray(String[]::new)));}
    private String mediaUrl(String key){return key==null?null:properties.media().publicBaseUrl()+"/"+key;}
    private static List<String> strings(Array array)throws SQLException{return array==null?List.of():List.of((String[])array.getArray());}
    private static Integer integer(ResultSet rs,String name)throws SQLException{int v=rs.getInt(name);return rs.wasNull()?null:v;}
    private static Long nullableLong(ResultSet rs,int index)throws SQLException{long v=rs.getLong(index);return rs.wasNull()?null:v;}
    private static Instant instant(ResultSet rs,String name)throws SQLException{var v=rs.getTimestamp(name);return v==null?null:v.toInstant();}
    private static Instant instant(ResultSet rs,int index)throws SQLException{var v=rs.getTimestamp(index);return v==null?null:v.toInstant();}
    private static String clean(String value){return value==null||value.isBlank()?null:value.trim();}
    private static <T>T value(T value,T fallback){return value==null?fallback:value;}
    private static ApiException notFound(String thing){return ApiException.notFound("DISCOVERY_NOT_FOUND",thing+" not found");}
    private static String encodeCursor(int offset){return Base64.getUrlEncoder().withoutPadding().encodeToString(("feed:"+offset).getBytes());}
    private static int decodeCursor(String cursor){if(cursor==null||cursor.isBlank())return 0;try{String v=new String(Base64.getUrlDecoder().decode(cursor));if(!v.startsWith("feed:"))throw new IllegalArgumentException();return Integer.parseInt(v.substring(5));}catch(RuntimeException ex){throw ApiException.badRequest("FEED_CURSOR_INVALID","Invalid feed cursor");}}
    private static String tokens(String input,String category,String city,long min,long median,String currency){return input.replace("{category}",category).replace("{city}",city).replace("{min_price}",money(min,currency)).replace("{median_price}",money(median,currency));}
    private static String money(long cents,String currency){return currency+" "+String.format(Locale.ROOT,"%,.0f",cents/100.0);}
    private record LandingStats(long vendorCount, Long minimum, Long maximum, Long median,
            String currency, String city) {}
}
