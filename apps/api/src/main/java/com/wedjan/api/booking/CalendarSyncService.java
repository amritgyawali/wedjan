package com.wedjan.api.booking;

import com.wedjan.api.booking.BookingDtos.CalendarExport;
import com.wedjan.api.booking.BookingDtos.CalendarSyncStatus;
import com.wedjan.api.booking.BookingDtos.ExternalCalendar;
import com.wedjan.api.booking.BookingDtos.ExternalCalendarRequest;
import com.wedjan.api.common.ApiException;
import com.wedjan.api.common.Uuidv7;
import com.wedjan.api.config.WedjanProperties;
import com.wedjan.api.auth.MailService;
import java.io.InputStream;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Period;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.Status;
import net.fortuna.ical4j.model.property.Transp;
import net.fortuna.ical4j.model.property.Uid;

@Service
public class CalendarSyncService {

    private static final Logger log = LoggerFactory.getLogger(CalendarSyncService.class);
    private static final DateTimeFormatter ICS_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter ICS_LOCAL_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final int MAX_REDIRECTS = 3;
    private static final int MAX_ICS_EVENTS = 5_000;
    private static final int MAX_BUSY_DAYS = 5_000;
    private static final int SYNC_CONCURRENCY = 8;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final WedjanProperties properties;
    private final MailService mail;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build();

    public CalendarSyncService(JdbcTemplate jdbc, TransactionTemplate transactions,
            WedjanProperties properties, MailService mail) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.properties = properties;
        this.mail = mail;
    }

    public List<ExternalCalendar> list(UUID vendorId) {
        return jdbc.query("""
                SELECT id,ics_url,last_synced_at,sync_status,last_error FROM external_calendars
                WHERE vendor_id=? AND deleted_at IS NULL ORDER BY created_at
                """, (rs, row) -> new ExternalCalendar(rs.getObject("id", UUID.class),
                rs.getString("ics_url"), rs.getTimestamp("last_synced_at") == null ? null
                        : rs.getTimestamp("last_synced_at").toInstant(),
                CalendarSyncStatus.valueOf(rs.getString("sync_status")), rs.getString("last_error")), vendorId);
    }

    public ExternalCalendar connect(UUID vendorId, ExternalCalendarRequest request) {
        URI uri = validateUri(request.icsUrl());
        UUID id = Uuidv7.next();
        transactions.executeWithoutResult(status -> {
            // Serialize the count + insert so concurrent connections cannot exceed the cap.
            UUID owner = jdbc.query("SELECT account_id FROM vendor_profiles WHERE account_id=? AND deleted_at IS NULL FOR UPDATE",
                    rs -> rs.next() ? rs.getObject(1, UUID.class) : null, vendorId);
            if (owner == null) throw ApiException.notFound("VENDOR_PROFILE_NOT_FOUND", "Vendor profile not found");
            Long existing = jdbc.queryForObject("""
                    SELECT count(*) FROM external_calendars WHERE vendor_id=? AND deleted_at IS NULL
                    """, Long.class, vendorId);
            if (existing != null && existing >= 10) {
                throw ApiException.conflict("CALENDAR_LIMIT_REACHED",
                        "Disconnect a calendar before adding another (maximum 10)");
            }
            try {
                jdbc.update("""
                        INSERT INTO external_calendars(id,vendor_id,ics_url,created_by)
                        VALUES (?,?,?,?)
                        """, id, vendorId, uri.toString(), vendorId);
            } catch (org.springframework.dao.DuplicateKeyException ex) {
                throw ApiException.conflict("CALENDAR_ALREADY_CONNECTED", "This calendar is already connected");
            }
        });
        sync(id);
        return get(vendorId, id);
    }

    public ExternalCalendar syncNow(UUID vendorId, UUID id) {
        requireOwned(vendorId, id);
        Boolean recentlySynced = jdbc.queryForObject("""
                SELECT last_synced_at > now()-interval '1 minute' FROM external_calendars
                WHERE id=? AND vendor_id=? AND deleted_at IS NULL
                """, Boolean.class, id, vendorId);
        if (Boolean.TRUE.equals(recentlySynced)) {
            throw ApiException.conflict("CALENDAR_SYNC_RATE_LIMITED",
                    "Wait a minute before refreshing this calendar again");
        }
        sync(id);
        return get(vendorId, id);
    }

    public void disconnect(UUID vendorId, UUID id) {
        requireOwned(vendorId, id);
        transactions.executeWithoutResult(status -> {
            jdbc.update("UPDATE availability_exceptions SET deleted_at=now(),updated_at=now() WHERE external_calendar_id=? AND deleted_at IS NULL", id);
            jdbc.update("UPDATE external_calendars SET deleted_at=now(),updated_at=now() WHERE id=? AND vendor_id=?", id, vendorId);
        });
    }

    public CalendarExport exportUrl(UUID vendorId, boolean rotate) {
        if (rotate) {
            jdbc.update("UPDATE vendor_profiles SET calendar_export_token=?,updated_at=now() WHERE account_id=?",
                    UUID.randomUUID(), vendorId);
        }
        UUID token = jdbc.queryForObject("SELECT calendar_export_token FROM vendor_profiles WHERE account_id=?",
                UUID.class, vendorId);
        return new CalendarExport(properties.publicApiUrl().replaceAll("/$", "")
                + "/api/v1/calendar/" + token + ".ics", Instant.now());
    }

    public String export(UUID token) {
        UUID vendorId = jdbc.query("""
                SELECT account_id FROM vendor_profiles WHERE calendar_export_token=? AND deleted_at IS NULL
                """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null, token);
        if (vendorId == null) throw ApiException.notFound("CALENDAR_FEED_NOT_FOUND", "Calendar feed not found");
        StringBuilder out = new StringBuilder("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//wedjan//Booking Calendar//EN\r\nCALSCALE:GREGORIAN\r\nMETHOD:PUBLISH\r\n");
        jdbc.query("""
                SELECT code,event_date,start_time,end_time,event_timezone,package_title_snap,venue_address,updated_at
                FROM bookings WHERE vendor_id=? AND status IN ('CONFIRMED','IN_PROGRESS','COMPLETED')
                  AND deleted_at IS NULL ORDER BY event_start_at
                """, rs -> {
            while (rs.next()) {
                out.append("BEGIN:VEVENT\r\nUID:").append(rs.getString("code")).append("@wedjan\r\n")
                        .append("DTSTAMP:").append(formatUtc(rs.getTimestamp("updated_at").toInstant())).append("\r\n");
                LocalDate date = rs.getObject("event_date", LocalDate.class);
                var start = rs.getObject("start_time", java.time.LocalTime.class);
                var end = rs.getObject("end_time", java.time.LocalTime.class);
                if (start == null || end == null) {
                    out.append("DTSTART;VALUE=DATE:").append(date.format(ICS_DATE)).append("\r\n")
                            .append("DTEND;VALUE=DATE:").append(date.plusDays(1).format(ICS_DATE)).append("\r\n");
                } else {
                    String zone = rs.getString("event_timezone");
                    out.append("DTSTART;TZID=").append(escape(zone)).append(":")
                            .append(date.atTime(start).format(ICS_LOCAL_DATE_TIME)).append("\r\n")
                            .append("DTEND;TZID=").append(escape(zone)).append(":")
                            .append(date.atTime(end).format(ICS_LOCAL_DATE_TIME)).append("\r\n");
                }
                out.append("SUMMARY:").append(escape(rs.getString("package_title_snap"))).append("\r\n");
                if (rs.getString("venue_address") != null) out.append("LOCATION:")
                        .append(escape(rs.getString("venue_address"))).append("\r\n");
                out.append("STATUS:CONFIRMED\r\nEND:VEVENT\r\n");
            }
            return null;
        }, vendorId);
        return out.append("END:VCALENDAR\r\n").toString();
    }

    @Scheduled(fixedDelayString = "${wedjan.calendar.sync-interval-ms:3600000}",
            initialDelayString = "${wedjan.calendar.sync-initial-delay-ms:60000}")
    public void syncDueCalendars() {
        List<UUID> ids = jdbc.query("""
                SELECT id FROM external_calendars WHERE deleted_at IS NULL
                  AND (last_synced_at IS NULL OR last_synced_at < now() - interval '55 minutes')
                  AND (sync_started_at IS NULL OR sync_started_at < now() - interval '5 minutes')
                ORDER BY last_synced_at NULLS FIRST LIMIT 500
                """, (rs, row) -> rs.getObject(1, UUID.class));
        if (ids.isEmpty()) return;
        try (var executor = Executors.newFixedThreadPool(SYNC_CONCURRENCY,
                Thread.ofVirtual().name("calendar-sync-", 0).factory())) {
            List<Callable<Void>> tasks = ids.stream().<Callable<Void>>map(id -> () -> {
                sync(id);
                return null;
            }).toList();
            executor.invokeAll(tasks);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Calendar sync batch interrupted");
        }
    }

    void sync(UUID id) {
        int claimed = jdbc.update("""
                UPDATE external_calendars SET sync_started_at=now(),updated_at=now()
                WHERE id=? AND deleted_at IS NULL
                  AND (sync_started_at IS NULL OR sync_started_at < now() - interval '5 minutes')
                """, id);
        if (claimed == 0) return;
        Map<String, Object> calendar = jdbc.query("""
                SELECT ec.id,ec.vendor_id,ec.ics_url,ec.etag,ec.last_modified,ec.sync_status,vp.timezone
                FROM external_calendars ec JOIN vendor_profiles vp ON vp.account_id=ec.vendor_id
                WHERE ec.id=? AND ec.deleted_at IS NULL
                """, rs -> rs.next() ? Map.of(
                        "vendorId", rs.getObject("vendor_id", UUID.class),
                        "url", rs.getString("ics_url"),
                        "etag", rs.getString("etag") == null ? "" : rs.getString("etag"),
                        "lastModified", rs.getString("last_modified") == null ? "" : rs.getString("last_modified"),
                        "syncStatus", rs.getString("sync_status"),
                        "timezone", rs.getString("timezone")) : null, id);
        if (calendar == null) return;
        try {
            FetchedCalendar response = fetch(calendar);
            if (response.statusCode() == 304) {
                jdbc.update("UPDATE external_calendars SET last_synced_at=now(),sync_started_at=NULL,sync_status='HEALTHY',last_error=NULL,updated_at=now() WHERE id=?", id);
                return;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Calendar returned HTTP " + response.statusCode());
            }
            String normalized = response.body().toUpperCase(Locale.ROOT);
            if (!normalized.contains("BEGIN:VCALENDAR") || !normalized.contains("END:VCALENDAR")) {
                throw new IllegalArgumentException("Calendar response is not valid iCalendar data");
            }
            List<BusyDay> days = parseBusyDays(response.body(), ZoneId.of((String) calendar.get("timezone")));
            transactions.executeWithoutResult(status -> {
                UUID activeCalendar = jdbc.query("""
                        SELECT id FROM external_calendars
                        WHERE id=? AND deleted_at IS NULL FOR UPDATE
                        """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null, id);
                // A disconnect that won the row lock must remain authoritative.
                if (activeCalendar == null) return;
                lockImportedDates((UUID) calendar.get("vendorId"), days);
                jdbc.update("UPDATE availability_exceptions SET deleted_at=now(),updated_at=now() WHERE external_calendar_id=? AND deleted_at IS NULL", id);
                for (BusyDay day : days) {
                    List<UUID> reusable = jdbc.query("""
                            SELECT id FROM availability_exceptions
                            WHERE external_calendar_id=? AND date=? AND source_ref=?
                            ORDER BY created_at DESC LIMIT 1
                            """, (rs, row) -> rs.getObject(1, UUID.class), id, day.date(), day.uid());
                    if (reusable.isEmpty()) {
                        jdbc.update("""
                                INSERT INTO availability_exceptions(id,vendor_id,date,type,slots,note,source,source_ref,external_calendar_id)
                                VALUES (?,?,?,'BLACKOUT','[]'::jsonb,'Busy on connected calendar','ICS',?,?)
                                """, Uuidv7.next(), calendar.get("vendorId"), day.date(), day.uid(), id);
                    } else {
                        jdbc.update("""
                                UPDATE availability_exceptions SET deleted_at=NULL,updated_at=now(),
                                  type='BLACKOUT',slots='[]'::jsonb,note='Busy on connected calendar'
                                WHERE id=?
                                """, reusable.getFirst());
                    }
                }
                jdbc.update("""
                        UPDATE external_calendars SET last_synced_at=now(),sync_status='HEALTHY',
                          sync_started_at=NULL,last_error=NULL,etag=?,last_modified=?,updated_at=now() WHERE id=?
                        """, response.etag(), response.lastModified(), id);
            });
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            int updated = jdbc.update("UPDATE external_calendars SET last_synced_at=now(),sync_started_at=NULL,sync_status='DEGRADED',last_error=?,updated_at=now() WHERE id=? AND deleted_at IS NULL",
                    message.substring(0, Math.min(message.length(), 1000)), id);
            if (updated > 0 && !"DEGRADED".equals(calendar.get("syncStatus"))) {
                String ownerEmail = jdbc.queryForObject("SELECT email FROM accounts WHERE id=?", String.class,
                        calendar.get("vendorId"));
                mail.sendCalendarDegraded(ownerEmail, properties.publicWebUrl() + "/vendor/calendar");
            }
            log.warn("Calendar {} sync degraded: {}", id, message);
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
        }
    }

    private FetchedCalendar fetch(Map<String, Object> calendar) throws Exception {
        URI uri = validateUri((String) calendar.get("url"));
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(15))
                    .header(HttpHeaders.ACCEPT, "text/calendar,*/*;q=0.5").GET();
            if (!((String) calendar.get("etag")).isBlank()) {
                builder.header(HttpHeaders.IF_NONE_MATCH, (String) calendar.get("etag"));
            }
            if (!((String) calendar.get("lastModified")).isBlank()) {
                builder.header(HttpHeaders.IF_MODIFIED_SINCE, (String) calendar.get("lastModified"));
            }
            HttpResponse<InputStream> response = http.send(builder.build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (isRedirect(response.statusCode())) {
                String location = response.headers().firstValue(HttpHeaders.LOCATION)
                        .orElseThrow(() -> new IllegalStateException("Calendar redirect has no Location"));
                response.body().close();
                if (redirects == MAX_REDIRECTS) {
                    throw new IllegalStateException("Calendar redirected too many times");
                }
                uri = validateUri(uri.resolve(location).toString());
                continue;
            }
            int maxBytes = (int) Math.min(10L * 1024 * 1024,
                    Math.max(1024, properties.calendar().maxResponseBytes()));
            byte[] bytes;
            try (InputStream body = response.body()) {
                bytes = body.readNBytes(maxBytes + 1);
            }
            if (bytes.length > maxBytes) {
                throw new IllegalArgumentException("Calendar response exceeds " + maxBytes + " bytes");
            }
            return new FetchedCalendar(response.statusCode(),
                    new String(bytes, StandardCharsets.UTF_8),
                    response.headers().firstValue(HttpHeaders.ETAG).orElse(null),
                    response.headers().firstValue(HttpHeaders.LAST_MODIFIED).orElse(null));
        }
        throw new IllegalStateException("Calendar redirect failed");
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    public static List<BusyDay> parseBusyDays(String content, ZoneId defaultZone) {
        try {
            var calendar = new CalendarBuilder().build(new StringReader(content));
            List<VEvent> events = calendar.getComponents();
            if (events.size() > MAX_ICS_EVENTS) {
                throw new IllegalArgumentException("Calendar contains too many events");
            }
            ZonedDateTime horizonStart = LocalDate.now(defaultZone).minusDays(1)
                    .atStartOfDay(defaultZone);
            ZonedDateTime horizonEnd = LocalDate.now(defaultZone).plusYears(5)
                    .plusDays(1).atStartOfDay(defaultZone);
            Period<Temporal> horizon = new Period<>((Temporal) horizonStart, (Temporal) horizonEnd);
            List<BusyDay> result = new ArrayList<>();
            for (VEvent event : events) {
                boolean cancelled = event.<Status>getProperty(Property.STATUS)
                        .map(Status::getValue).map(Status.VALUE_CANCELLED::equalsIgnoreCase).orElse(false);
                boolean transparent = event.getTransparency().map(Transp::getValue)
                        .map(Transp.VALUE_TRANSPARENT::equalsIgnoreCase).orElse(false);
                if (cancelled || transparent) continue;
                String uid = event.<Uid>getProperty(Property.UID).map(Uid::getValue)
                        .orElseGet(() -> UUID.randomUUID().toString());
                for (Period<Temporal> occurrence : event.calculateRecurrenceSet(horizon)) {
                    addOccurrenceDays(result, occurrence, uid, defaultZone);
                    if (result.size() > MAX_BUSY_DAYS) {
                        throw new IllegalArgumentException("Calendar expands to too many busy dates");
                    }
                }
            }
            return result.stream().distinct().toList();
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid iCalendar data", ex);
        }
    }

    private static void addOccurrenceDays(List<BusyDay> result, Period<Temporal> occurrence,
            String uid, ZoneId targetZone) {
        IcsPoint start = point(occurrence.getStart(), targetZone);
        IcsPoint end = point(occurrence.getEnd(), targetZone);
        LocalDate lastBlocked = end.date();
        if (start.dateOnly() || end.dateOnly()
                || (end.date().isAfter(start.date()) && end.time().equals(LocalTime.MIDNIGHT))) {
            lastBlocked = end.date().minusDays(1);
        }
        if (lastBlocked.isBefore(start.date())) lastBlocked = start.date();
        for (LocalDate date = start.date(); !date.isAfter(lastBlocked); date = date.plusDays(1)) {
            String sourceRef = uid + ":" + date;
            result.add(new BusyDay(date, sourceRef.substring(0, Math.min(sourceRef.length(), 500))));
        }
    }

    private void lockImportedDates(UUID vendorId, List<BusyDay> days) {
        List<LocalDate> dates = days.stream().map(BusyDay::date).distinct().sorted().toList();
        for (LocalDate date : dates) {
            String lockKey = "booking:lock:" + vendorId + ":" + date;
            jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",
                    (org.springframework.jdbc.core.RowCallbackHandler) rs -> {}, lockKey);
            List<String> conflicts = jdbc.query("""
                    SELECT code FROM bookings WHERE vendor_id=? AND event_date=?
                      AND (status IN ('REQUESTED','VENDOR_ACCEPTED','PENDING_PAYMENT','CONFIRMED','IN_PROGRESS')
                           OR (status='DRAFT' AND hold_expires_at>now()))
                      AND deleted_at IS NULL ORDER BY created_at LIMIT 3
                    """, (rs, row) -> rs.getString(1), vendorId, date);
            if (!conflicts.isEmpty()) {
                throw new IllegalStateException("Calendar busy date " + date
                        + " conflicts with active booking " + String.join(", ", conflicts));
            }
        }
    }

    private static IcsPoint point(Temporal value, ZoneId targetZone) {
        if (value instanceof LocalDate date) return new IcsPoint(date, LocalTime.MIDNIGHT, true);
        if (value instanceof ZonedDateTime valueWithZone) {
            ZonedDateTime target = valueWithZone.withZoneSameInstant(targetZone);
            return new IcsPoint(target.toLocalDate(), target.toLocalTime(), false);
        }
        if (value instanceof OffsetDateTime valueWithOffset) {
            ZonedDateTime target = valueWithOffset.atZoneSameInstant(targetZone);
            return new IcsPoint(target.toLocalDate(), target.toLocalTime(), false);
        }
        if (value instanceof Instant instant) {
            ZonedDateTime target = instant.atZone(targetZone);
            return new IcsPoint(target.toLocalDate(), target.toLocalTime(), false);
        }
        if (value instanceof LocalDateTime local) {
            return new IcsPoint(local.toLocalDate(), local.toLocalTime(), false);
        }
        throw new IllegalArgumentException("Unsupported iCalendar date type: " + value.getClass().getName());
    }

    private ExternalCalendar get(UUID vendorId, UUID id) {
        return list(vendorId).stream().filter(item -> item.id().equals(id)).findFirst()
                .orElseThrow(() -> ApiException.notFound("CALENDAR_NOT_FOUND", "Connected calendar not found"));
    }

    private void requireOwned(UUID vendorId, UUID id) {
        if (get(vendorId, id) == null) throw ApiException.notFound("CALENDAR_NOT_FOUND", "Connected calendar not found");
    }

    private URI validateUri(String raw) {
        try {
            URI uri = URI.create(raw.trim());
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null) throw new IllegalArgumentException();
            if (!properties.calendar().allowPrivateUrls()) {
                for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                    if (isPrivateAddress(address)) throw new IllegalArgumentException();
                }
            }
            return uri;
        } catch (Exception ex) {
            throw ApiException.badRequest("CALENDAR_URL_INVALID",
                    "Calendar URL must resolve to a public http(s) host");
        }
    }

    private static boolean isPrivateAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) return true;
        byte[] bytes = address.getAddress();
        if (bytes.length == 16) return (bytes[0] & 0xfe) == 0xfc;
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        return first == 0 || first == 127 || (first == 100 && second >= 64 && second <= 127)
                || (first == 169 && second == 254) || (first == 198 && (second == 18 || second == 19));
    }

    private static String formatUtc(Instant instant) {
        return DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneId.of("UTC")).format(instant);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace(";", "\\;")
                .replace(",", "\\,").replace("\r", "").replace("\n", "\\n");
    }

    public record BusyDay(LocalDate date, String uid) {}
    private record IcsPoint(LocalDate date, LocalTime time, boolean dateOnly) {}
    private record FetchedCalendar(int statusCode, String body, String etag, String lastModified) {}
}
