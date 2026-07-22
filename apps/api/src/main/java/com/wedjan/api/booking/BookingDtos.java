package com.wedjan.api.booking;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BookingDtos {

    private BookingDtos() {}

    public enum AvailabilityMode { DATE, SLOT }
    public enum VenueLocationMode { VENDOR, TRAVEL }
    public enum AvailabilityStatus { AVAILABLE, LIMITED, BOOKED, BLACKED_OUT }
    public enum ExceptionType { BLACKOUT, EXTRA_OPEN, CUSTOM_SLOTS }
    public enum CalendarSyncStatus { PENDING, HEALTHY, DEGRADED }
    public enum BookingStatus {
        DRAFT, REQUESTED, VENDOR_ACCEPTED, PENDING_PAYMENT, CONFIRMED,
        IN_PROGRESS, COMPLETED, CANCELLED_BY_CUSTOMER, CANCELLED_BY_VENDOR,
        DECLINED, EXPIRED, DISPUTED
    }
    public enum BookingActor { CUSTOMER, VENDOR, SYSTEM, ADMIN }
    public enum DeclineReason {
        NOT_AVAILABLE, SCOPE_MISMATCH, PRICE_MISMATCH, SCHEDULING_CONFLICT, OTHER
    }

    public record AvailabilitySlot(
            @NotNull LocalTime start,
            @NotNull LocalTime end,
            @Size(max = 80) String label) {}

    public record AvailabilityRuleRequest(
            @Min(0) @Max(6) int weekday,
            boolean isAvailable,
            @Size(max = 48) List<@NotNull @Valid AvailabilitySlot> slots,
            @Min(1) @Max(100) int jobsPerDay) {}

    public record AvailabilitySettingsRequest(
            @NotNull AvailabilityMode mode,
            @NotBlank @Size(max = 64) String timezone,
            @NotEmpty @Size(min = 7, max = 7) List<@Valid AvailabilityRuleRequest> rules) {}

    public record AvailabilityRule(UUID id, int weekday, boolean isAvailable,
            List<AvailabilitySlot> slots, int jobsPerDay) {}

    public record AvailabilityExceptionRequest(
            @NotNull @FutureOrPresent LocalDate date,
            @NotNull ExceptionType type,
            @Size(max = 48) List<@NotNull @Valid AvailabilitySlot> slots,
            @Size(max = 500) String note) {}

    public record AvailabilityException(UUID id, LocalDate date, ExceptionType type,
            List<AvailabilitySlot> slots, String note, String source, UUID externalCalendarId) {}

    public record AvailabilitySettings(AvailabilityMode mode, String timezone,
            List<AvailabilityRule> rules, List<AvailabilityException> exceptions) {}

    public record SlotAvailability(LocalTime start, LocalTime end, String label,
            AvailabilityStatus status) {}

    public record AvailabilityDay(LocalDate date, AvailabilityStatus status,
            int capacity, int occupied, List<SlotAvailability> slots) {}

    public record AvailabilityResponse(UUID vendorId, String vendorSlug,
            AvailabilityMode mode, String timezone, LocalDate from, LocalDate to,
            Instant serverNow, List<AvailabilityDay> days) {}

    public record ExternalCalendarRequest(@NotBlank @Size(max = 2048) String icsUrl) {}

    public record ExternalCalendar(UUID id, String icsUrl, Instant lastSyncedAt,
            CalendarSyncStatus syncStatus, String lastError) {}

    public record CalendarExport(String url, Instant rotatedAt) {}

    public record BookingAddOnRequest(@NotNull UUID addOnId,
            @Min(1) @Max(999) int qty) {}

    public record BookingConfigurationRequest(
            @NotNull UUID packageId,
            @NotNull @FutureOrPresent LocalDate eventDate,
            LocalTime startTime,
            LocalTime endTime,
            @NotBlank @Size(max = 64) String eventTimezone,
            @Min(1) @Max(100000) Integer guests,
            @NotNull VenueLocationMode venueLocationMode,
            @Size(max = 500) String venueAddress,
            @Size(max = 120) String venueCity,
            @Pattern(regexp = "(?i)^[A-Z]{2}$") String venueCountry,
            @Min(-90) @Max(90) Double venueLat,
            @Min(-180) @Max(180) Double venueLng,
            @Size(max = 3000) String notes,
            @Size(max = 50) List<@NotNull @Valid BookingAddOnRequest> addOns,
            @Min(0) Long expectedPackageVersion) {}

    public record PriceBreakdown(String currency, long subtotalCents, long addOnsCents,
            long travelFeeCents, long discountCents, long totalCents, int depositPct,
            long depositCents, String cancellationPolicy) {}

    public record BookingAddOn(UUID addOnId, String title, int qty,
            long unitPriceCents, long totalCents) {}

    public record BookingQuote(UUID vendorId, String vendorSlug, String packageTitle,
            String bookingMode, PriceBreakdown price, List<BookingAddOn> addOns,
            AvailabilityStatus availability, Instant quoteExpiresAt) {}

    public record BookingEvent(UUID id, BookingStatus fromStatus, BookingStatus toStatus,
            BookingActor actor, UUID actorId, Map<String, Object> metadata, Instant createdAt) {}

    public record Reschedule(UUID id, LocalDate eventDate, LocalTime startTime,
            LocalTime endTime, String eventTimezone, boolean customerApproved,
            boolean vendorApproved, String status, Instant createdAt) {}

    public record Booking(UUID id, String code, UUID customerId, UUID vendorId,
            String vendorSlug, String vendorName, UUID packageId, String packageTitle,
            String bookingMode, LocalDate eventDate, LocalTime startTime, LocalTime endTime,
            String eventTimezone, Integer guests, VenueLocationMode venueLocationMode,
            String venueAddress, String venueCity, String venueCountry, Double venueLat,
            Double venueLng, String notes,
            PriceBreakdown price, List<BookingAddOn> addOns,
            BookingStatus status, Instant holdExpiresAt, Instant slaDueAt,
            Instant paymentDueAt, Instant confirmedAt, Instant completedAt,
            Instant disputeWindowEndsAt, String cancelReason, boolean vendorPenaltyFlag,
            long version, Instant createdAt, Instant serverNow, List<String> allowedActions,
            List<BookingEvent> events, Reschedule pendingReschedule) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BookingList(List<Booking> items, String nextCursor, Instant serverNow) {}

    public record BookingAcceptRequest(@Min(0) Long travelFeeCents,
            @Size(max = 50) List<@NotNull @Valid BookingAddOnRequest> addOns,
            @Min(0) long version) {}

    public record BookingDeclineRequest(@NotNull DeclineReason reason,
            @Size(max = 1000) String message, @Min(0) long version) {}

    public record BookingVersionRequest(@Min(0) long version) {}

    public record BookingCancelRequest(@NotBlank @Size(max = 1000) String reason,
            @Min(0) long version, UUID refundCalculationId) {}

    public record BookingDisputeRequest(@NotBlank @Size(max = 2000) String reason,
            @Min(0) long version) {}

    public record RefundCalculation(UUID calculationId, UUID bookingId, String policy, int daysToEvent,
            int refundPercent, long refundableCents, long nonRefundableCents,
            boolean vendorPenalty, Instant calculatedAt) {}

    public record RescheduleProposalRequest(
            @NotNull @FutureOrPresent LocalDate eventDate,
            LocalTime startTime,
            LocalTime endTime,
            @NotBlank @Size(max = 64) String eventTimezone,
            @Min(0) long version) {}

    public record RescheduleDecisionRequest(boolean accept, @Min(0) long version) {}
}
