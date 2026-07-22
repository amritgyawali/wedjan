package com.wedjan.api.booking;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wedjan.api.booking.BookingDtos.AvailabilityDay;
import com.wedjan.api.booking.BookingDtos.AvailabilityException;
import com.wedjan.api.booking.BookingDtos.AvailabilityExceptionRequest;
import com.wedjan.api.booking.BookingDtos.AvailabilityMode;
import com.wedjan.api.booking.BookingDtos.AvailabilityResponse;
import com.wedjan.api.booking.BookingDtos.AvailabilityRule;
import com.wedjan.api.booking.BookingDtos.AvailabilitySettings;
import com.wedjan.api.booking.BookingDtos.AvailabilitySettingsRequest;
import com.wedjan.api.booking.BookingDtos.AvailabilitySlot;
import com.wedjan.api.booking.BookingDtos.AvailabilityStatus;
import com.wedjan.api.booking.BookingDtos.SlotAvailability;
import com.wedjan.api.common.ApiException;
import com.wedjan.api.common.Uuidv7;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AvailabilityService {

    private static final TypeReference<List<AvailabilitySlot>> SLOT_LIST = new TypeReference<>() {};
    private static final int MAX_PUBLIC_RANGE_DAYS = 366;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AvailabilityService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public AvailabilitySettings settings(UUID vendorId) {
        ensureVendor(vendorId);
        var header = jdbc.queryForMap("""
                SELECT availability_mode, timezone FROM vendor_profiles
                WHERE account_id=? AND deleted_at IS NULL
                """, vendorId);
        return new AvailabilitySettings(
                AvailabilityMode.valueOf((String) header.get("availability_mode")),
                (String) header.get("timezone"), rules(vendorId), exceptions(vendorId));
    }

    @Transactional
    public AvailabilitySettings updateSettings(UUID vendorId, AvailabilitySettingsRequest request) {
        ensureVendor(vendorId);
        validateTimezone(request.timezone());
        advisoryLock("booking:vendor:" + vendorId);
        Map<String, Object> current = jdbc.queryForMap(
                "SELECT availability_mode,timezone FROM vendor_profiles WHERE account_id=? AND deleted_at IS NULL",
                vendorId);
        boolean modeChanged = !request.mode().name().equals(current.get("availability_mode"));
        boolean timezoneChanged = !request.timezone().equals(current.get("timezone"));
        if (modeChanged || timezoneChanged) {
            Long active = jdbc.queryForObject("""
                    SELECT count(*) FROM bookings WHERE vendor_id=? AND deleted_at IS NULL
                      AND status IN ('DRAFT','REQUESTED','VENDOR_ACCEPTED','PENDING_PAYMENT',
                                     'CONFIRMED','IN_PROGRESS')
                    """, Long.class, vendorId);
            if (active != null && active > 0) {
                throw ApiException.conflict(
                        modeChanged ? "AVAILABILITY_MODE_ACTIVE_BOOKINGS"
                                : "AVAILABILITY_TIMEZONE_ACTIVE_BOOKINGS",
                        "Resolve active bookings and requests before changing calendar mode or timezone");
            }
        }
        Map<Integer, AvailabilityRule> currentRules = rules(vendorId).stream()
                .collect(java.util.stream.Collectors.toMap(AvailabilityRule::weekday, rule -> rule));
        Set<Integer> activeWeekdays = new HashSet<>(jdbc.query("""
                SELECT DISTINCT extract(dow FROM event_date)::integer
                FROM bookings WHERE vendor_id=? AND deleted_at IS NULL AND (
                  status IN ('REQUESTED','VENDOR_ACCEPTED','PENDING_PAYMENT','CONFIRMED','IN_PROGRESS')
                  OR (status='DRAFT' AND hold_expires_at>now()))
                """, (rs, row) -> rs.getInt(1), vendorId));
        Set<Integer> weekdays = new HashSet<>();
        for (var rule : request.rules()) {
            if (!weekdays.add(rule.weekday())) {
                throw ApiException.badRequest("AVAILABILITY_RULE_DUPLICATE",
                        "Each weekday must appear exactly once");
            }
            validateSlots(rule.slots(), request.mode() == AvailabilityMode.SLOT && rule.isAvailable());
            AvailabilityRule prior = currentRules.get(rule.weekday());
            boolean changed = prior == null || prior.isAvailable() != rule.isAvailable()
                    || prior.jobsPerDay() != rule.jobsPerDay()
                    || !sortedSlots(prior.slots()).equals(sortedSlots(rule.slots()));
            if (changed && activeWeekdays.contains(rule.weekday())) {
                throw ApiException.conflict("AVAILABILITY_RULE_ACTIVE_BOOKINGS",
                        "Resolve active bookings and requests on this weekday before changing its weekly rule");
            }
        }
        if (weekdays.size() != 7) {
            throw ApiException.badRequest("AVAILABILITY_RULES_INCOMPLETE",
                    "Provide one rule for every weekday");
        }
        jdbc.update("UPDATE vendor_profiles SET availability_mode=?,timezone=?,updated_at=now(),version=version+1 WHERE account_id=?",
                request.mode().name(), request.timezone(), vendorId);
        jdbc.update("UPDATE availability_rules SET deleted_at=now(),updated_at=now() WHERE vendor_id=? AND deleted_at IS NULL",
                vendorId);
        for (var rule : request.rules()) {
            jdbc.update("""
                    INSERT INTO availability_rules(id,vendor_id,weekday,is_available,slots,jobs_per_day,created_by)
                    VALUES (?,?,?,?,?::jsonb,?,?)
                    """, Uuidv7.next(), vendorId, rule.weekday(), rule.isAvailable(),
                    slotsJson(rule.slots()), rule.jobsPerDay(), vendorId);
        }
        return settings(vendorId);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public AvailabilityException addException(UUID vendorId, AvailabilityExceptionRequest request) {
        ensureVendor(vendorId);
        validateException(request);
        lockBlackoutDate(vendorId, request.date(), request.type().name());
        requireDateCanBeBlocked(vendorId, request.date(), request.type().name());
        UUID id = Uuidv7.next();
        try {
            jdbc.update("""
                    INSERT INTO availability_exceptions(id,vendor_id,date,type,slots,note,source,created_by)
                    VALUES (?,?,?,?,?::jsonb,?,'MANUAL',?)
                    """, id, vendorId, request.date(), request.type().name(),
                    slotsJson(request.slots()), clean(request.note()), vendorId);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw ApiException.conflict("AVAILABILITY_EXCEPTION_EXISTS",
                    "This date already has a manual exception; edit it instead");
        }
        return exception(vendorId, id);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public AvailabilityException updateException(UUID vendorId, UUID id,
            AvailabilityExceptionRequest request) {
        validateException(request);
        lockBlackoutDate(vendorId, request.date(), request.type().name());
        requireDateCanBeBlocked(vendorId, request.date(), request.type().name());
        int changed = jdbc.update("""
                UPDATE availability_exceptions SET date=?,type=?,slots=?::jsonb,note=?,updated_at=now()
                WHERE id=? AND vendor_id=? AND source='MANUAL' AND deleted_at IS NULL
                """, request.date(), request.type().name(), slotsJson(request.slots()),
                clean(request.note()), id, vendorId);
        if (changed == 0) throw ApiException.notFound("AVAILABILITY_EXCEPTION_NOT_FOUND",
                "Manual availability exception not found");
        return exception(vendorId, id);
    }

    @Transactional
    public void deleteException(UUID vendorId, UUID id) {
        int changed = jdbc.update("""
                UPDATE availability_exceptions SET deleted_at=now(),updated_at=now()
                WHERE id=? AND vendor_id=? AND source='MANUAL' AND deleted_at IS NULL
                """, id, vendorId);
        if (changed == 0) throw ApiException.notFound("AVAILABILITY_EXCEPTION_NOT_FOUND",
                "Manual availability exception not found");
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse publicAvailability(String slug, LocalDate from, LocalDate to) {
        validateRange(from, to);
        List<AvailabilityResponse> responses = jdbc.query("""
                SELECT account_id,slug,availability_mode,timezone FROM vendor_profiles
                WHERE slug=? AND is_public AND status='VERIFIED' AND deleted_at IS NULL
                """, (rs, row) -> response(rs.getObject("account_id", UUID.class),
                        rs.getString("slug"), AvailabilityMode.valueOf(rs.getString("availability_mode")),
                        rs.getString("timezone"), from, to), slug);
        if (responses.isEmpty()) {
            throw ApiException.notFound("VENDOR_NOT_FOUND", "Vendor not found");
        }
        return responses.getFirst();
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse vendorCalendar(UUID vendorId, LocalDate from, LocalDate to) {
        validateRange(from, to);
        ensureVendor(vendorId);
        return jdbc.queryForObject("""
                SELECT account_id,slug,availability_mode,timezone FROM vendor_profiles
                WHERE account_id=? AND deleted_at IS NULL
                """, (rs, row) -> response(rs.getObject("account_id", UUID.class),
                        rs.getString("slug"), AvailabilityMode.valueOf(rs.getString("availability_mode")),
                        rs.getString("timezone"), from, to), vendorId);
    }

    /** Called only while BookingService holds the vendor/date advisory lock. */
    @Transactional
    public int requireBookable(UUID vendorId, LocalDate eventDate, LocalTime start,
            LocalTime end, AvailabilityMode mode, UUID excludedBookingId) {
        jdbc.update("UPDATE booking_holds SET released_at=now() WHERE vendor_id=? AND released_at IS NULL AND expires_at<=now()",
                vendorId);
        var availability = jdbc.queryForObject(
                "SELECT status,capacity,occupied FROM get_availability(?,?,?)",
                (rs, row) -> new DayFact(AvailabilityStatus.valueOf(rs.getString("status")),
                        rs.getInt("capacity"), rs.getInt("occupied")),
                vendorId, eventDate, eventDate);
        if (availability == null || availability.status() == AvailabilityStatus.BLACKED_OUT
                || availability.status() == AvailabilityStatus.BOOKED) {
            throw ApiException.conflict("BOOKING_DATE_UNAVAILABLE", "The selected date is no longer available");
        }
        if (mode == AvailabilityMode.SLOT) {
            requireDefinedSlot(vendorId, eventDate, start, end);
            if (slotOccupied(vendorId, eventDate, start, end, excludedBookingId)) {
                throw ApiException.conflict(
                    "BOOKING_SLOT_UNAVAILABLE", "The selected time block is no longer available");
            }
            return 1;
        }
        int capacity = availability.capacity();
        Set<Integer> occupied = new HashSet<>(jdbc.query("""
                SELECT capacity_slot FROM bookings WHERE vendor_id=? AND event_date=?
                  AND status IN ('PENDING_PAYMENT','CONFIRMED','IN_PROGRESS') AND deleted_at IS NULL
                  AND (?::uuid IS NULL OR id<>?::uuid)
                UNION SELECT capacity_slot FROM booking_holds WHERE vendor_id=? AND event_date=?
                  AND released_at IS NULL AND expires_at>now()
                """, (rs, row) -> rs.getInt(1), vendorId, eventDate, excludedBookingId,
                excludedBookingId, vendorId, eventDate));
        for (int slot = 1; slot <= capacity; slot++) if (!occupied.contains(slot)) return slot;
        throw ApiException.conflict("BOOKING_DATE_UNAVAILABLE", "The selected date is no longer available");
    }

    @Transactional(readOnly = true)
    public void requireQuotedSlotBookable(UUID vendorId, LocalDate eventDate,
            LocalTime start, LocalTime end, AvailabilityStatus dayStatus) {
        if (dayStatus == null || dayStatus == AvailabilityStatus.BLACKED_OUT
                || dayStatus == AvailabilityStatus.BOOKED) {
            throw ApiException.conflict("BOOKING_DATE_UNAVAILABLE",
                    "The selected date is no longer available");
        }
        requireDefinedSlot(vendorId, eventDate, start, end);
        if (slotOccupied(vendorId, eventDate, start, end, null)) {
            throw ApiException.conflict("BOOKING_SLOT_UNAVAILABLE",
                    "The selected time block is no longer available");
        }
    }

    @Transactional(readOnly = true)
    public void requireNotBlackedOut(UUID vendorId, LocalDate eventDate) {
        String status = jdbc.queryForObject("SELECT status FROM get_availability(?,?,?)",
                String.class, vendorId, eventDate, eventDate);
        if (AvailabilityStatus.BLACKED_OUT.name().equals(status)) {
            throw ApiException.conflict("BOOKING_DATE_UNAVAILABLE",
                    "The vendor is no longer available on this date; choose another date");
        }
    }

    @Transactional(readOnly = true)
    public VendorCalendarProfile profile(UUID vendorId) {
        List<VendorCalendarProfile> profiles = jdbc.query("""
                SELECT availability_mode,timezone,calendar_export_token FROM vendor_profiles
                WHERE account_id=? AND deleted_at IS NULL
                """, (rs, row) -> new VendorCalendarProfile(
                        AvailabilityMode.valueOf(rs.getString("availability_mode")),
                        rs.getString("timezone"), rs.getObject("calendar_export_token", UUID.class)), vendorId);
        if (profiles.isEmpty()) throw ApiException.notFound("VENDOR_NOT_FOUND", "Vendor profile not found");
        return profiles.getFirst();
    }

    private AvailabilityResponse response(UUID vendorId, String slug, AvailabilityMode mode,
            String timezone, LocalDate from, LocalDate to) {
        List<AvailabilityDay> baseDays = jdbc.query("""
                SELECT date,status,capacity,occupied FROM get_availability(?,?,?)
                """, (rs, row) -> {
                    LocalDate date = rs.getObject("date", LocalDate.class);
                    return new AvailabilityDay(date, AvailabilityStatus.valueOf(rs.getString("status")),
                            rs.getInt("capacity"), rs.getInt("occupied"), List.of());
                }, vendorId, from, to);
        List<AvailabilityDay> days = baseDays;
        if (mode == AvailabilityMode.SLOT) {
            Map<LocalDate, List<AvailabilitySlot>> slots = effectiveSlots(vendorId, from, to);
            Map<LocalDate, List<TimeRange>> conflicts = slotConflicts(vendorId, from, to);
            days = baseDays.stream().map(day -> new AvailabilityDay(day.date(), day.status(),
                    day.capacity(), day.occupied(), slots.getOrDefault(day.date(), List.of()).stream()
                    .map(slot -> new SlotAvailability(slot.start(), slot.end(), slot.label(),
                            day.status() == AvailabilityStatus.BLACKED_OUT
                                    ? AvailabilityStatus.BLACKED_OUT
                                    : conflicts.getOrDefault(day.date(), List.of()).stream()
                                    .anyMatch(conflict -> conflict.start().isBefore(slot.end())
                                            && conflict.end().isAfter(slot.start()))
                                    ? AvailabilityStatus.BOOKED : AvailabilityStatus.AVAILABLE))
                    .toList())).toList();
        }
        return new AvailabilityResponse(vendorId, slug, mode, timezone, from, to, Instant.now(), days);
    }

    private Map<LocalDate, List<AvailabilitySlot>> effectiveSlots(UUID vendorId,
            LocalDate from, LocalDate to) {
        var result = new java.util.HashMap<LocalDate, List<AvailabilitySlot>>();
        jdbc.query("""
                WITH days AS (
                  SELECT day::date FROM generate_series(?::date,?::date,interval '1 day') day
                )
                SELECT day,COALESCE(
                  (SELECT slots::text FROM availability_exceptions WHERE vendor_id=? AND date=day
                     AND type='CUSTOM_SLOTS' AND deleted_at IS NULL
                     ORDER BY source='MANUAL' DESC LIMIT 1),
                  (SELECT slots::text FROM availability_rules WHERE vendor_id=?
                     AND weekday=extract(dow FROM day)::smallint AND deleted_at IS NULL LIMIT 1),
                  '[]') slots
                FROM days ORDER BY day
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                result.put(rs.getObject("day", LocalDate.class), parseSlots(rs.getString("slots"))),
                from, to, vendorId, vendorId);
        return result;
    }

    private Map<LocalDate, List<TimeRange>> slotConflicts(UUID vendorId,
            LocalDate from, LocalDate to) {
        var result = new java.util.HashMap<LocalDate, List<TimeRange>>();
        jdbc.query("""
                SELECT event_date,start_time,end_time FROM bookings
                WHERE vendor_id=? AND event_date BETWEEN ? AND ?
                  AND status IN ('PENDING_PAYMENT','CONFIRMED','IN_PROGRESS')
                  AND start_time IS NOT NULL AND end_time IS NOT NULL AND deleted_at IS NULL
                UNION ALL
                SELECT h.event_date,b.start_time,b.end_time
                FROM booking_holds h JOIN bookings b ON b.id=h.booking_id
                WHERE h.vendor_id=? AND h.event_date BETWEEN ? AND ?
                  AND h.released_at IS NULL AND h.expires_at>now()
                  AND b.start_time IS NOT NULL AND b.end_time IS NOT NULL
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                result.computeIfAbsent(rs.getObject("event_date", LocalDate.class), ignored -> new ArrayList<>())
                        .add(new TimeRange(rs.getObject("start_time", LocalTime.class),
                                rs.getObject("end_time", LocalTime.class))),
                vendorId, from, to, vendorId, from, to);
        return result;
    }

    private List<AvailabilitySlot> effectiveSlots(UUID vendorId, LocalDate date) {
        String value = jdbc.queryForObject("""
                SELECT COALESCE(
                    (SELECT slots::text FROM availability_exceptions WHERE vendor_id=? AND date=?
                       AND type='CUSTOM_SLOTS' AND deleted_at IS NULL ORDER BY source='MANUAL' DESC LIMIT 1),
                    (SELECT slots::text FROM availability_rules WHERE vendor_id=?
                       AND weekday=extract(dow FROM ?::date)::smallint AND deleted_at IS NULL LIMIT 1),
                    '[]')
                """, String.class, vendorId, date, vendorId, date);
        return parseSlots(value);
    }

    private void requireDefinedSlot(UUID vendorId, LocalDate eventDate,
            LocalTime start, LocalTime end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw ApiException.badRequest("BOOKING_SLOT_REQUIRED", "Choose a valid start and end time");
        }
        boolean defined = effectiveSlots(vendorId, eventDate).stream()
                .anyMatch(slot -> start.equals(slot.start()) && end.equals(slot.end()));
        if (!defined) throw ApiException.conflict("BOOKING_SLOT_UNAVAILABLE",
                "Choose one of this vendor's defined time blocks");
    }

    private boolean slotOccupied(UUID vendorId, LocalDate eventDate,
            LocalTime start, LocalTime end, UUID excludedBookingId) {
        Boolean occupied = jdbc.queryForObject("""
                WITH requested AS (
                  SELECT tstzrange(
                    (?::date + ?::time) AT TIME ZONE vp.timezone,
                    (?::date + ?::time) AT TIME ZONE vp.timezone,
                    '[)') AS event_window
                  FROM vendor_profiles vp WHERE vp.account_id=? AND vp.deleted_at IS NULL
                )
                SELECT EXISTS (
                  SELECT 1 FROM bookings b,requested r
                  WHERE b.vendor_id=? AND b.event_window && r.event_window
                    AND b.status IN ('PENDING_PAYMENT','CONFIRMED','IN_PROGRESS')
                    AND b.deleted_at IS NULL AND (?::uuid IS NULL OR b.id<>?::uuid)
                  UNION ALL
                  SELECT 1 FROM booking_holds h,requested r
                  WHERE h.vendor_id=? AND h.event_window && r.event_window
                    AND h.released_at IS NULL AND h.expires_at>now()
                    AND (?::uuid IS NULL OR h.booking_id<>?::uuid)
                )
                """, Boolean.class, eventDate, start, eventDate, end, vendorId,
                vendorId, excludedBookingId, excludedBookingId,
                vendorId, excludedBookingId, excludedBookingId);
        return Boolean.TRUE.equals(occupied);
    }

    private List<AvailabilityRule> rules(UUID vendorId) {
        return jdbc.query("""
                SELECT id,weekday,is_available,slots::text,jobs_per_day FROM availability_rules
                WHERE vendor_id=? AND deleted_at IS NULL ORDER BY weekday
                """, (rs, row) -> new AvailabilityRule(rs.getObject("id", UUID.class),
                rs.getInt("weekday"), rs.getBoolean("is_available"),
                parseSlots(rs.getString("slots")), rs.getInt("jobs_per_day")), vendorId);
    }

    private List<AvailabilityException> exceptions(UUID vendorId) {
        return jdbc.query("""
                SELECT id,date,type,slots::text,note,source,external_calendar_id
                FROM availability_exceptions WHERE vendor_id=? AND deleted_at IS NULL
                ORDER BY date,id
                """, this::mapException, vendorId);
    }

    private AvailabilityException exception(UUID vendorId, UUID id) {
        List<AvailabilityException> values = jdbc.query("""
                SELECT id,date,type,slots::text,note,source,external_calendar_id
                FROM availability_exceptions WHERE id=? AND vendor_id=? AND deleted_at IS NULL
                """, this::mapException, id, vendorId);
        if (values.isEmpty()) throw ApiException.notFound("AVAILABILITY_EXCEPTION_NOT_FOUND",
                "Availability exception not found");
        return values.getFirst();
    }

    private AvailabilityException mapException(ResultSet rs, int row) throws SQLException {
        return new AvailabilityException(rs.getObject("id", UUID.class),
                rs.getObject("date", LocalDate.class),
                BookingDtos.ExceptionType.valueOf(rs.getString("type")),
                parseSlots(rs.getString("slots")), rs.getString("note"), rs.getString("source"),
                rs.getObject("external_calendar_id", UUID.class));
    }

    private void requireDateCanBeBlocked(UUID vendorId, LocalDate date, String type) {
        if (!"BLACKOUT".equals(type)) return;
        List<String> blocking = jdbc.query("""
                SELECT code FROM bookings WHERE vendor_id=? AND event_date=?
                  AND (status IN ('REQUESTED','VENDOR_ACCEPTED','PENDING_PAYMENT','CONFIRMED','IN_PROGRESS')
                       OR (status='DRAFT' AND hold_expires_at>now()))
                  AND deleted_at IS NULL ORDER BY created_at LIMIT 3
                """, (rs, row) -> rs.getString(1), vendorId, date);
        if (!blocking.isEmpty()) throw ApiException.conflict("AVAILABILITY_ACTIVE_BOOKING",
                "Resolve active booking " + String.join(", ", blocking) + " before blacking out this date");
    }

    private void validateException(AvailabilityExceptionRequest request) {
        validateSlots(request.slots(), request.type() == BookingDtos.ExceptionType.CUSTOM_SLOTS);
        if (request.type() != BookingDtos.ExceptionType.CUSTOM_SLOTS
                && request.slots() != null && !request.slots().isEmpty()) {
            throw ApiException.badRequest("AVAILABILITY_SLOTS_INVALID",
                    "Only CUSTOM_SLOTS exceptions may contain slots");
        }
    }

    private static void validateSlots(List<AvailabilitySlot> slots, boolean required) {
        List<AvailabilitySlot> sorted = sortedSlots(slots);
        if (required && sorted.isEmpty()) throw ApiException.badRequest("AVAILABILITY_SLOTS_REQUIRED",
                "At least one slot is required");
        LocalTime previousEnd = null;
        for (var slot : sorted) {
            if (slot.start() == null || slot.end() == null || !slot.end().isAfter(slot.start())) {
                throw ApiException.badRequest("AVAILABILITY_SLOT_INVALID", "Every slot must end after it starts");
            }
            if (previousEnd != null && slot.start().isBefore(previousEnd)) {
                throw ApiException.badRequest("AVAILABILITY_SLOT_OVERLAP", "Availability slots cannot overlap");
            }
            previousEnd = slot.end();
        }
    }

    private static List<AvailabilitySlot> sortedSlots(List<AvailabilitySlot> slots) {
        return slots == null ? List.of() : slots.stream()
                .sorted(Comparator.comparing(AvailabilitySlot::start)).toList();
    }

    private static void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw ApiException.badRequest("AVAILABILITY_RANGE_INVALID", "Provide a valid from/to date range");
        }
        if (Duration.between(from.atStartOfDay(), to.plusDays(1).atStartOfDay()).toDays()
                > MAX_PUBLIC_RANGE_DAYS) {
            throw ApiException.badRequest("AVAILABILITY_RANGE_TOO_LARGE", "Availability range is limited to 366 days");
        }
    }

    private static void validateTimezone(String timezone) {
        try { ZoneId.of(timezone); }
        catch (RuntimeException ex) { throw ApiException.badRequest("AVAILABILITY_TIMEZONE_INVALID", "Invalid timezone"); }
    }

    private void ensureVendor(UUID vendorId) {
        Long count = vendorId == null ? 0L : jdbc.queryForObject("""
                SELECT count(*) FROM vendor_profiles WHERE account_id=? AND deleted_at IS NULL
                """, Long.class, vendorId);
        if (count == null || count == 0) {
            throw ApiException.notFound("VENDOR_NOT_FOUND", "Vendor profile not found");
        }
    }

    private void lockBlackoutDate(UUID vendorId, LocalDate date, String type) {
        if (!"BLACKOUT".equals(type)) return;
        advisoryLock("booking:lock:" + vendorId + ":" + date);
    }

    private void advisoryLock(String key) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> {}, key);
    }

    private String slotsJson(List<AvailabilitySlot> slots) {
        try { return json.writeValueAsString(slots == null ? List.of() : slots); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Could not encode slots", ex); }
    }

    private List<AvailabilitySlot> parseSlots(String value) {
        try { return value == null ? List.of() : json.readValue(value, SLOT_LIST); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Invalid stored availability slots", ex); }
    }

    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private record DayFact(AvailabilityStatus status, int capacity, int occupied) {}
    private record TimeRange(LocalTime start, LocalTime end) {}
    public record VendorCalendarProfile(AvailabilityMode mode, String timezone, UUID exportToken) {}
}
