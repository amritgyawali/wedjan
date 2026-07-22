package com.wedjan.api.booking;

import com.wedjan.api.booking.BookingDtos.Booking;
import com.wedjan.api.booking.BookingDtos.BookingAcceptRequest;
import com.wedjan.api.booking.BookingDtos.BookingCancelRequest;
import com.wedjan.api.booking.BookingDtos.BookingConfigurationRequest;
import com.wedjan.api.booking.BookingDtos.BookingDeclineRequest;
import com.wedjan.api.booking.BookingDtos.BookingDisputeRequest;
import com.wedjan.api.booking.BookingDtos.BookingList;
import com.wedjan.api.booking.BookingDtos.BookingQuote;
import com.wedjan.api.booking.BookingDtos.BookingStatus;
import com.wedjan.api.booking.BookingDtos.BookingVersionRequest;
import com.wedjan.api.booking.BookingDtos.RefundCalculation;
import com.wedjan.api.booking.BookingDtos.RescheduleDecisionRequest;
import com.wedjan.api.booking.BookingDtos.RescheduleProposalRequest;
import com.wedjan.api.auth.RateLimitService;
import com.wedjan.api.config.JwtAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class BookingController {

    private final BookingService service;
    private final RateLimitService rateLimits;

    public BookingController(BookingService service, RateLimitService rateLimits) {
        this.service = service;
        this.rateLimits = rateLimits;
    }

    @PostMapping("/bookings/quote")
    @PreAuthorize("hasRole('CUSTOMER')")
    public BookingQuote quote(@Valid @RequestBody BookingConfigurationRequest request) {
        return service.quote(accountId(), request);
    }

    @PostMapping("/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    public Booking create(@RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody BookingConfigurationRequest request,
            HttpServletRequest servletRequest) {
        rateLimits.check("booking-create-ip", servletRequest.getRemoteAddr(), 120, Duration.ofHours(1));
        return service.create(accountId(), idempotencyKey, request);
    }

    @GetMapping("/bookings")
    @PreAuthorize("hasRole('CUSTOMER')")
    public BookingList list(@RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return service.customerBookings(accountId(), status, cursor, limit);
    }

    @GetMapping("/vendors/me/bookings")
    @PreAuthorize("hasRole('VENDOR')")
    public BookingList vendorInbox(@RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return service.vendorBookings(accountId(), status, cursor, limit);
    }

    @GetMapping("/bookings/{id}")
    public Booking get(@PathVariable UUID id) { return service.get(accountId(), id); }

    @PostMapping("/bookings/{id}/checkout")
    @PreAuthorize("hasRole('CUSTOMER')")
    public Booking checkout(@PathVariable UUID id,
            @Valid @RequestBody BookingVersionRequest request) {
        return service.checkout(accountId(), id, request);
    }

    @PostMapping("/bookings/{id}/confirm")
    @PreAuthorize("hasRole('CUSTOMER')")
    public Booking confirm(@PathVariable UUID id,
            @Valid @RequestBody BookingVersionRequest request) {
        return service.confirmPaymentStub(accountId(), id, request);
    }

    @PostMapping("/bookings/{id}/accept")
    @PreAuthorize("hasRole('VENDOR')")
    public Booking accept(@PathVariable UUID id,
            @Valid @RequestBody BookingAcceptRequest request) {
        return service.accept(accountId(), id, request);
    }

    @PostMapping("/bookings/{id}/decline")
    @PreAuthorize("hasRole('VENDOR')")
    public Booking decline(@PathVariable UUID id,
            @Valid @RequestBody BookingDeclineRequest request) {
        return service.decline(accountId(), id, request);
    }

    @GetMapping("/bookings/{id}/refund-preview")
    public RefundCalculation refundPreview(@PathVariable UUID id) {
        return service.refundPreview(accountId(), id);
    }

    @PostMapping("/bookings/{id}/cancel")
    public Booking cancel(@PathVariable UUID id,
            @Valid @RequestBody BookingCancelRequest request) {
        return service.cancel(accountId(), id, request);
    }

    @PostMapping("/bookings/{id}/complete")
    @PreAuthorize("hasRole('VENDOR')")
    public Booking complete(@PathVariable UUID id,
            @Valid @RequestBody BookingVersionRequest request) {
        return service.complete(accountId(), id, request);
    }

    @PostMapping("/bookings/{id}/dispute")
    @PreAuthorize("hasRole('CUSTOMER')")
    public Booking dispute(@PathVariable UUID id,
            @Valid @RequestBody BookingDisputeRequest request) {
        return service.dispute(accountId(), id, request);
    }

    @PostMapping("/bookings/{id}/reschedule-propose")
    public Booking proposeReschedule(@PathVariable UUID id,
            @Valid @RequestBody RescheduleProposalRequest request) {
        return service.proposeReschedule(accountId(), id, request);
    }

    @PostMapping("/bookings/{id}/reschedule-confirm")
    public Booking decideReschedule(@PathVariable UUID id,
            @Valid @RequestBody RescheduleDecisionRequest request) {
        return service.decideReschedule(accountId(), id, request);
    }

    private static UUID accountId() { return JwtAuthFilter.currentAccountId(); }
}
