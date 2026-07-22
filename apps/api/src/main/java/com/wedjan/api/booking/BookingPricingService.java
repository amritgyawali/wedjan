package com.wedjan.api.booking;

import com.wedjan.api.booking.BookingDtos.AvailabilityMode;
import com.wedjan.api.booking.BookingDtos.BookingAddOn;
import com.wedjan.api.booking.BookingDtos.BookingAddOnRequest;
import com.wedjan.api.booking.BookingDtos.BookingConfigurationRequest;
import com.wedjan.api.booking.BookingDtos.PriceBreakdown;
import com.wedjan.api.booking.BookingDtos.VenueLocationMode;
import com.wedjan.api.common.ApiException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingPricingService {

    private final JdbcTemplate jdbc;

    public BookingPricingService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public CalculatedQuote calculate(BookingConfigurationRequest request) {
        PackageSnapshot pack = packageSnapshot(request.packageId());
        if (request.expectedPackageVersion() != null
                && request.expectedPackageVersion() != pack.version()) {
            throw ApiException.conflict("BOOKING_PACKAGE_CHANGED",
                    "This package changed; review the latest price and terms");
        }
        validateGuests(pack, request.guests());
        int minutes = durationMinutes(pack, request.startTime(), request.endTime());
        ResolvedVenue venue = resolveVenue(pack, request);
        try {
            long subtotal = pricedAmount(pack.priceCents(), pack.pricingModel(), 1,
                    request.guests(), minutes);
            List<BookingAddOn> addOns = addOns(pack, request.addOns(), request.guests(), minutes);
            long addOnTotal = addOns.stream().mapToLong(BookingAddOn::totalCents)
                    .reduce(0L, Math::addExact);
            long travelFee = venue.mode() == VenueLocationMode.VENDOR ? 0
                    : travelFee(pack.vendorId(), venue.city(), venue.country(),
                            venue.lat(), venue.lng());
            long total = Math.addExact(Math.addExact(subtotal, addOnTotal), travelFee);
            long deposit = ceilPercent(total, pack.depositPct());
            return new CalculatedQuote(pack, new PriceBreakdown(pack.currency(), subtotal,
                    addOnTotal, travelFee, 0, total, pack.depositPct(), deposit,
                    pack.cancellationPolicy()), addOns, venue);
        } catch (ArithmeticException ex) {
            throw ApiException.badRequest("BOOKING_PRICE_OVERFLOW", "Configured price is too large");
        }
    }

    public PackageSnapshot packageSnapshot(UUID packageId) {
        List<PackageSnapshot> rows = jdbc.query("""
                SELECT p.id,p.vendor_id,vp.slug vendor_slug,vp.business_name vendor_name,
                  p.title,p.description_md,p.whats_included_md,p.whats_excluded_md,p.price_cents,
                  p.currency,p.pricing_model,p.booking_mode,p.deposit_pct,p.cancellation_policy,
                  p.version,p.allow_same_day,p.duration_minutes,p.min_guests,p.max_guests,
                  vp.availability_mode,vp.timezone,vp.base_city,vp.base_country,vp.lat,vp.lng
                FROM packages p JOIN vendor_profiles vp ON vp.account_id=p.vendor_id
                WHERE p.id=? AND p.status='PUBLISHED' AND p.deleted_at IS NULL
                  AND vp.status='VERIFIED' AND vp.is_public AND vp.deleted_at IS NULL
                """, this::mapPackage, packageId);
        if (rows.isEmpty()) throw ApiException.notFound("BOOKING_PACKAGE_NOT_FOUND",
                "Published package not found");
        return rows.getFirst();
    }

    private List<BookingAddOn> addOns(PackageSnapshot pack, List<BookingAddOnRequest> selected,
            Integer guests, int minutes) {
        if (selected == null || selected.isEmpty()) return List.of();
        Set<UUID> seen = new HashSet<>();
        List<BookingAddOn> result = new ArrayList<>();
        for (BookingAddOnRequest selection : selected) {
            if (!seen.add(selection.addOnId())) throw ApiException.badRequest("BOOKING_ADD_ON_DUPLICATE",
                    "Select each add-on once and adjust its quantity");
            List<AddOnPrice> found = jdbc.query("""
                    SELECT id,title,price_cents,pricing_model,max_qty FROM add_ons
                    WHERE id=? AND vendor_id=? AND (package_id IS NULL OR package_id=?) AND deleted_at IS NULL
                    """, (rs, row) -> new AddOnPrice(rs.getObject("id", UUID.class),
                    rs.getString("title"), rs.getLong("price_cents"), rs.getString("pricing_model"),
                    (Integer) rs.getObject("max_qty")), selection.addOnId(), pack.vendorId(), pack.id());
            if (found.isEmpty()) throw ApiException.badRequest("BOOKING_ADD_ON_INVALID",
                    "An add-on is not available for this package");
            AddOnPrice addOn = found.getFirst();
            if (addOn.maxQty() != null && selection.qty() > addOn.maxQty()) {
                throw ApiException.badRequest("BOOKING_ADD_ON_QUANTITY",
                        addOn.title() + " allows at most " + addOn.maxQty());
            }
            long line = pricedAmount(addOn.priceCents(), addOn.pricingModel(), selection.qty(),
                    guests, minutes);
            result.add(new BookingAddOn(addOn.id(), addOn.title(), selection.qty(),
                    addOn.priceCents(), line));
        }
        return List.copyOf(result);
    }

    private long travelFee(UUID vendorId, String cityValue, String countryValue,
            Double lat, Double lng) {
        String city = clean(cityValue);
        String country = clean(countryValue);
        if ((city == null) != (country == null)) {
            throw ApiException.badRequest("BOOKING_VENUE_REGION_INCOMPLETE",
                    "Provide both venue city and two-letter country code");
        }
        if (country != null) country = country.toUpperCase(Locale.ROOT);
        if ((lat == null) != (lng == null)) {
            throw ApiException.badRequest("BOOKING_VENUE_LOCATION_INCOMPLETE",
                    "Provide both venue latitude and longitude");
        }
        Long configuredAreas = jdbc.queryForObject("""
                SELECT count(*) FROM service_areas WHERE vendor_id=? AND deleted_at IS NULL
                """, Long.class, vendorId);
        if (configuredAreas == null || configuredAreas == 0) {
            throw ApiException.conflict("BOOKING_OUTSIDE_SERVICE_AREA",
                    "This vendor has no active service area");
        }
        if (city == null && lat == null) {
            throw ApiException.badRequest("BOOKING_VENUE_LOCATION_REQUIRED",
                    "Choose a venue city and country or provide venue coordinates");
        }
        List<Long> fees = jdbc.query("""
                SELECT COALESCE(travel_fee_cents,0) FROM service_areas
                WHERE vendor_id=? AND deleted_at IS NULL AND (
                  (mode='REGION' AND lower(city)=lower(?) AND country=?) OR
                  (mode='CITY_RADIUS' AND lat IS NOT NULL AND lng IS NOT NULL AND radius_km IS NOT NULL AND
                  earth_distance(ll_to_earth(lat,lng),ll_to_earth(?,?)) <= radius_km*1000))
                ORDER BY COALESCE(travel_fee_cents,0),id LIMIT 1
                """, (rs, row) -> rs.getLong(1), vendorId, city, country, lat, lng);
        if (fees.isEmpty()) throw ApiException.conflict("BOOKING_OUTSIDE_SERVICE_AREA",
                "The venue is outside this vendor's service area");
        return fees.getFirst();
    }

    private static ResolvedVenue resolveVenue(PackageSnapshot pack,
            BookingConfigurationRequest request) {
        if (request.venueLocationMode() == null) {
            throw ApiException.badRequest("BOOKING_VENUE_MODE_REQUIRED",
                    "Choose vendor location or a travel venue");
        }
        if (request.venueLocationMode() == VenueLocationMode.VENDOR) {
            if (clean(request.venueAddress()) != null || clean(request.venueCity()) != null
                    || clean(request.venueCountry()) != null || request.venueLat() != null
                    || request.venueLng() != null) {
                throw ApiException.badRequest("BOOKING_VENDOR_VENUE_FIELDS_NOT_ALLOWED",
                        "Do not provide travel venue fields when booking at the vendor location");
            }
            String city = clean(pack.baseCity());
            String country = clean(pack.baseCountry());
            if (city == null || country == null) {
                throw ApiException.conflict("BOOKING_VENDOR_LOCATION_UNAVAILABLE",
                        "This vendor has not configured a bookable base location");
            }
            return new ResolvedVenue(VenueLocationMode.VENDOR, null, city,
                    country.toUpperCase(Locale.ROOT), pack.baseLat(), pack.baseLng());
        }
        return new ResolvedVenue(VenueLocationMode.TRAVEL, clean(request.venueAddress()),
                clean(request.venueCity()), clean(request.venueCountry()), request.venueLat(),
                request.venueLng());
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static long pricedAmount(long priceCents, String model, int qty,
            Integer guests, int minutes) {
        long units = switch (model.toUpperCase(Locale.ROOT)) {
            case "FLAT", "STARTING_AT" -> 1;
            case "PER_GUEST" -> {
                if (guests == null) throw ApiException.badRequest("BOOKING_GUESTS_REQUIRED",
                        "Guest count is required for this price");
                yield guests;
            }
            case "PER_HOUR" -> Math.max(1, minutes);
            default -> throw ApiException.badRequest("BOOKING_PRICING_MODEL_INVALID",
                    "Unsupported pricing model");
        };
        long value = Math.multiplyExact(priceCents, qty);
        if ("PER_HOUR".equalsIgnoreCase(model)) {
            return Math.floorDiv(Math.addExact(Math.multiplyExact(value, units), 59), 60);
        }
        return Math.multiplyExact(value, units);
    }

    private static long ceilPercent(long cents, int pct) {
        return Math.floorDiv(Math.addExact(Math.multiplyExact(cents, pct), 99), 100);
    }

    private static int durationMinutes(PackageSnapshot pack, LocalTime start, LocalTime end) {
        if (start != null && end != null && end.isAfter(start)) {
            return Math.toIntExact(ChronoUnit.MINUTES.between(start, end));
        }
        return pack.durationMinutes() == null ? 60 : pack.durationMinutes();
    }

    private static void validateGuests(PackageSnapshot pack, Integer guests) {
        if (pack.minGuests() != null && (guests == null || guests < pack.minGuests())) {
            throw ApiException.badRequest("BOOKING_GUESTS_MIN", "This package requires at least " + pack.minGuests() + " guests");
        }
        if (pack.maxGuests() != null && guests != null && guests > pack.maxGuests()) {
            throw ApiException.badRequest("BOOKING_GUESTS_MAX", "This package supports at most " + pack.maxGuests() + " guests");
        }
    }

    private PackageSnapshot mapPackage(ResultSet rs, int row) throws SQLException {
        return new PackageSnapshot(rs.getObject("id", UUID.class), rs.getObject("vendor_id", UUID.class),
                rs.getString("vendor_slug"), rs.getString("vendor_name"), rs.getString("title"),
                rs.getString("description_md"), rs.getString("whats_included_md"),
                rs.getString("whats_excluded_md"), rs.getLong("price_cents"), rs.getString("currency"),
                rs.getString("pricing_model"), rs.getString("booking_mode"), rs.getInt("deposit_pct"),
                rs.getString("cancellation_policy"), rs.getLong("version"), rs.getBoolean("allow_same_day"),
                (Integer) rs.getObject("duration_minutes"), (Integer) rs.getObject("min_guests"),
                (Integer) rs.getObject("max_guests"), AvailabilityMode.valueOf(rs.getString("availability_mode")),
                rs.getString("timezone"), rs.getString("base_city"), rs.getString("base_country"),
                nullableDouble(rs, "lat"), nullableDouble(rs, "lng"));
    }

    public record CalculatedQuote(PackageSnapshot pack, PriceBreakdown price,
            List<BookingAddOn> addOns, ResolvedVenue venue) {}
    public record ResolvedVenue(VenueLocationMode mode, String address, String city,
            String country, Double lat, Double lng) {}
    public record PackageSnapshot(UUID id, UUID vendorId, String vendorSlug, String vendorName,
            String title, String descriptionMd, String whatsIncludedMd, String whatsExcludedMd,
            long priceCents, String currency, String pricingModel, String bookingMode,
            int depositPct, String cancellationPolicy, long version, boolean allowSameDay,
            Integer durationMinutes, Integer minGuests, Integer maxGuests,
            AvailabilityMode availabilityMode, String vendorTimezone, String baseCity,
            String baseCountry, Double baseLat, Double baseLng) {}
    private record AddOnPrice(UUID id, String title, long priceCents, String pricingModel,
            Integer maxQty) {}

    private static Double nullableDouble(ResultSet rs, String name) throws SQLException {
        double value = rs.getDouble(name);
        return rs.wasNull() ? null : value;
    }
}
