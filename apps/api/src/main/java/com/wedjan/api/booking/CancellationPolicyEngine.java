package com.wedjan.api.booking;

import com.wedjan.api.booking.BookingDtos.RefundCalculation;
import com.wedjan.api.common.ApiException;
import com.wedjan.api.common.Uuidv7;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Pure, cent-safe policy calculation shared by preview and Phase 5 execution. */
@Component
public class CancellationPolicyEngine {

    private final Clock clock;

    public CancellationPolicyEngine() {
        this(Clock.systemUTC());
    }

    CancellationPolicyEngine(Clock clock) {
        this.clock = clock;
    }

    public RefundCalculation calculate(UUID bookingId, String policy, LocalDate eventDate,
            String eventTimezone, long paidCents, boolean vendorInitiated) {
        ZoneId zone;
        try {
            zone = ZoneId.of(eventTimezone);
        } catch (RuntimeException ex) {
            throw ApiException.badRequest("BOOKING_TIMEZONE_INVALID", "Invalid event timezone");
        }
        LocalDate today = LocalDate.now(clock.withZone(zone));
        int days = Math.toIntExact(ChronoUnit.DAYS.between(today, eventDate));
        int percent = vendorInitiated ? 100 : refundPercent(policy, days);
        long refundable = Math.addExact(
                Math.multiplyExact(Math.floorDiv(paidCents, 100), percent),
                Math.floorDiv(Math.multiplyExact(Math.floorMod(paidCents, 100), percent), 100));
        return new RefundCalculation(Uuidv7.next(), bookingId, policy, days, percent, refundable,
                paidCents - refundable, vendorInitiated, Instant.now(clock));
    }

    /**
     * The named partial-refund boundary is the final non-zero tier. The lower
     * zero-day values in the product brief are examples within that zero tier;
     * this closes the brief's otherwise unspecified gaps deterministically.
     */
    public int refundPercent(String policy, int daysToEvent) {
        return switch (policy) {
            case "FLEXIBLE" -> daysToEvent >= 30 ? 100 : daysToEvent >= 14 ? 50 : 0;
            case "MODERATE" -> daysToEvent >= 60 ? 100 : daysToEvent >= 30 ? 50 : 0;
            case "STRICT" -> daysToEvent >= 90 ? 100 : daysToEvent >= 45 ? 25 : 0;
            default -> throw ApiException.badRequest("BOOKING_POLICY_INVALID",
                    "Unknown cancellation policy");
        };
    }
}
