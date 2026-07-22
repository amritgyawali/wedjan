package com.wedjan.api.booking;

import com.wedjan.api.booking.BookingDtos.AvailabilityException;
import com.wedjan.api.booking.BookingDtos.AvailabilityExceptionRequest;
import com.wedjan.api.booking.BookingDtos.AvailabilityResponse;
import com.wedjan.api.booking.BookingDtos.AvailabilitySettings;
import com.wedjan.api.booking.BookingDtos.AvailabilitySettingsRequest;
import com.wedjan.api.booking.BookingDtos.CalendarExport;
import com.wedjan.api.booking.BookingDtos.ExternalCalendar;
import com.wedjan.api.booking.BookingDtos.ExternalCalendarRequest;
import com.wedjan.api.config.JwtAuthFilter;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AvailabilityController {

    private final AvailabilityService availability;
    private final CalendarSyncService calendars;

    public AvailabilityController(AvailabilityService availability, CalendarSyncService calendars) {
        this.availability = availability;
        this.calendars = calendars;
    }

    @GetMapping("/vendors/{slug}/availability")
    public ResponseEntity<AvailabilityResponse> publicAvailability(@PathVariable String slug,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(java.time.Duration.ofSeconds(60)).cachePublic())
                .body(availability.publicAvailability(slug, from, to));
    }

    @GetMapping("/vendors/me/availability")
    @PreAuthorize("hasRole('VENDOR')")
    public AvailabilitySettings settings() { return availability.settings(accountId()); }

    @PutMapping("/vendors/me/availability")
    @PreAuthorize("hasRole('VENDOR')")
    public AvailabilitySettings updateSettings(@Valid @RequestBody AvailabilitySettingsRequest request) {
        return availability.updateSettings(accountId(), request);
    }

    @GetMapping("/vendors/me/calendar")
    @PreAuthorize("hasRole('VENDOR')")
    public AvailabilityResponse calendar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return availability.vendorCalendar(accountId(), from, to);
    }

    @PostMapping("/vendors/me/availability/exceptions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('VENDOR')")
    public AvailabilityException addException(@Valid @RequestBody AvailabilityExceptionRequest request) {
        return availability.addException(accountId(), request);
    }

    @PatchMapping("/vendors/me/availability/exceptions/{id}")
    @PreAuthorize("hasRole('VENDOR')")
    public AvailabilityException updateException(@PathVariable UUID id,
            @Valid @RequestBody AvailabilityExceptionRequest request) {
        return availability.updateException(accountId(), id, request);
    }

    @DeleteMapping("/vendors/me/availability/exceptions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('VENDOR')")
    public void deleteException(@PathVariable UUID id) { availability.deleteException(accountId(), id); }

    @GetMapping("/vendors/me/calendars")
    @PreAuthorize("hasRole('VENDOR')")
    public List<ExternalCalendar> calendars() { return calendars.list(accountId()); }

    @PostMapping("/vendors/me/calendars")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('VENDOR')")
    public ExternalCalendar connect(@Valid @RequestBody ExternalCalendarRequest request) {
        return calendars.connect(accountId(), request);
    }

    @PostMapping("/vendors/me/calendars/{id}/sync")
    @PreAuthorize("hasRole('VENDOR')")
    public ExternalCalendar sync(@PathVariable UUID id) { return calendars.syncNow(accountId(), id); }

    @DeleteMapping("/vendors/me/calendars/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('VENDOR')")
    public void disconnect(@PathVariable UUID id) { calendars.disconnect(accountId(), id); }

    @GetMapping("/vendors/me/calendar-export")
    @PreAuthorize("hasRole('VENDOR')")
    public CalendarExport exportUrl() { return calendars.exportUrl(accountId(), false); }

    @PostMapping("/vendors/me/calendar-export/rotate")
    @PreAuthorize("hasRole('VENDOR')")
    public CalendarExport rotateExportUrl() { return calendars.exportUrl(accountId(), true); }

    @GetMapping(value = "/calendar/{token}.ics", produces = "text/calendar")
    public ResponseEntity<String> export(@PathVariable UUID token) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/calendar;charset=UTF-8"))
                .cacheControl(CacheControl.noCache()).body(calendars.export(token));
    }

    private static UUID accountId() { return JwtAuthFilter.currentAccountId(); }
}
