package com.wedjan.api.booking;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wedjan.api.audit.AuditService;
import com.wedjan.api.auth.MailService;
import com.wedjan.api.auth.RateLimitService;
import com.wedjan.api.booking.BookingDtos.AvailabilityMode;
import com.wedjan.api.booking.BookingDtos.AvailabilityStatus;
import com.wedjan.api.booking.BookingDtos.Booking;
import com.wedjan.api.booking.BookingDtos.BookingAcceptRequest;
import com.wedjan.api.booking.BookingDtos.BookingActor;
import com.wedjan.api.booking.BookingDtos.BookingCancelRequest;
import com.wedjan.api.booking.BookingDtos.BookingConfigurationRequest;
import com.wedjan.api.booking.BookingDtos.BookingDeclineRequest;
import com.wedjan.api.booking.BookingDtos.BookingDisputeRequest;
import com.wedjan.api.booking.BookingDtos.BookingEvent;
import com.wedjan.api.booking.BookingDtos.BookingList;
import com.wedjan.api.booking.BookingDtos.BookingQuote;
import com.wedjan.api.booking.BookingDtos.BookingStatus;
import com.wedjan.api.booking.BookingDtos.BookingVersionRequest;
import com.wedjan.api.booking.BookingDtos.PriceBreakdown;
import com.wedjan.api.booking.BookingDtos.RefundCalculation;
import com.wedjan.api.booking.BookingDtos.Reschedule;
import com.wedjan.api.booking.BookingDtos.RescheduleDecisionRequest;
import com.wedjan.api.booking.BookingDtos.RescheduleProposalRequest;
import com.wedjan.api.booking.BookingDtos.VenueLocationMode;
import com.wedjan.api.booking.BookingPricingService.CalculatedQuote;
import com.wedjan.api.booking.BookingPricingService.PackageSnapshot;
import com.wedjan.api.common.ApiException;
import com.wedjan.api.common.Sha256;
import com.wedjan.api.common.Uuidv7;
import com.wedjan.api.config.AppConfigService;
import com.wedjan.api.config.WedjanProperties;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    private static final Duration CHECKOUT_HOLD = Duration.ofMinutes(15);
    private static final Duration REQUEST_SLA = Duration.ofHours(24);
    private static final Duration PAYMENT_WINDOW = Duration.ofHours(24);
    private static final String VENDOR_BOOKING_PRIORITY =
            "CASE WHEN b.status='REQUESTED' THEN 0 WHEN b.status='PENDING_PAYMENT' THEN 1 ELSE 2 END";
    private static final String VENDOR_BOOKING_DEADLINE =
            "COALESCE(b.sla_due_at,b.payment_due_at,b.event_start_at)";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final DefaultRedisScript<Long> COMPARE_DELETE = new DefaultRedisScript<>(
            "if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end",
            Long.class);

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final AvailabilityService availability;
    private final BookingPricingService pricing;
    private final BookingStateMachine stateMachine;
    private final CancellationPolicyEngine cancellationPolicies;
    private final AuditService audit;
    private final MailService mail;
    private final RateLimitService rateLimits;
    private final AppConfigService appConfig;
    private final WedjanProperties properties;

    public BookingService(JdbcTemplate jdbc, StringRedisTemplate redis, ObjectMapper json,
            AvailabilityService availability, BookingPricingService pricing,
            BookingStateMachine stateMachine, CancellationPolicyEngine cancellationPolicies,
            AuditService audit, MailService mail, RateLimitService rateLimits,
            AppConfigService appConfig, WedjanProperties properties) {
        this.jdbc = jdbc;
        this.redis = redis;
        this.json = json;
        this.availability = availability;
        this.pricing = pricing;
        this.stateMachine = stateMachine;
        this.cancellationPolicies = cancellationPolicies;
        this.audit = audit;
        this.mail = mail;
        this.rateLimits = rateLimits;
        this.appConfig = appConfig;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public BookingQuote quote(UUID customerId, BookingConfigurationRequest request) {
        requireBookingsEnabled();
        CalculatedQuote quote = pricing.calculate(request);
        requireNotSelfBooking(customerId, quote.pack().vendorId());
        EventTime time = eventTime(request, quote.pack());
        requireSameDayAllowed(time, quote.pack().allowSameDay());
        AvailabilityStatus status = jdbc.queryForObject(
                "SELECT status FROM get_availability(?,?,?)", AvailabilityStatus.class,
                quote.pack().vendorId(), request.eventDate(), request.eventDate());
        if (quote.pack().availabilityMode() == AvailabilityMode.SLOT) {
            availability.requireQuotedSlotBookable(quote.pack().vendorId(), request.eventDate(),
                    request.startTime(), request.endTime(), status);
        }
        return new BookingQuote(quote.pack().vendorId(), quote.pack().vendorSlug(),
                quote.pack().title(), quote.pack().bookingMode(), quote.price(), quote.addOns(),
                status, Instant.now().plusSeconds(60));
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Booking create(UUID customerId, String idempotencyKey,
            BookingConfigurationRequest request) {
        requireBookingsEnabled();
        String key = requireIdempotencyKey(idempotencyKey);
        String requestHash = hashRequest(request);
        advisoryLock("booking-idempotency:" + customerId + ":" + key);
        BookingRow replay = findByIdempotency(customerId, key);
        if (replay != null) {
            if (!replay.idempotencyRequestHash().equals(requestHash)) {
                throw ApiException.conflict("BOOKING_IDEMPOTENCY_MISMATCH",
                        "This Idempotency-Key was already used with different booking details");
            }
            return detail(replay, BookingActor.CUSTOMER);
        }

        CalculatedQuote preliminaryQuote = pricing.calculate(request);
        advisoryLock("booking:vendor:" + preliminaryQuote.pack().vendorId());
        CalculatedQuote quote = pricing.calculate(request);
        requireNotSelfBooking(customerId, quote.pack().vendorId());
        rateLimits.check("booking-create-account", customerId.toString(), 60, Duration.ofHours(1));
        rateLimits.check("booking-create-vendor", customerId + ":" + quote.pack().vendorId(),
                30, Duration.ofHours(1));
        advisoryLock("booking:customer-quota:" + customerId);
        enforceCreateQuota(customerId, quote.pack().vendorId(),
                "INSTANT".equals(quote.pack().bookingMode()));
        EventTime time = eventTime(request, quote.pack());
        requireSameDayAllowed(time, quote.pack().allowSameDay());
        return withCapacityLock(quote.pack().vendorId(), request.eventDate(), () -> {
            int capacitySlot = availability.requireBookable(quote.pack().vendorId(),
                    request.eventDate(), request.startTime(), request.endTime(),
                    quote.pack().availabilityMode(), null);
            UUID id = Uuidv7.next();
            Instant now = Instant.now();
            boolean instant = "INSTANT".equals(quote.pack().bookingMode());
            BookingStatus initial = instant ? BookingStatus.DRAFT : BookingStatus.REQUESTED;
            Instant holdUntil = instant ? now.plus(CHECKOUT_HOLD) : null;
            Instant slaDue = instant ? null : now.plus(REQUEST_SLA);
            String code = nextCode();
            String terms = packageTerms(quote.pack());
            try {
                jdbc.update("""
                    INSERT INTO bookings(id,code,idempotency_key,idempotency_request_hash,customer_id,
                      vendor_id,package_id,package_version,event_date,start_time,end_time,event_timezone,
                      event_start_at,event_end_at,availability_mode_snap,capacity_slot,guests,venue_location_mode,venue_address,
                      venue_city,venue_country,venue_lat,venue_lng,notes,package_title_snap,package_terms_snap,currency,subtotal_cents,
                      addons_cents,travel_fee_cents,discount_cents,total_cents,deposit_pct,
                      cancellation_policy_snap,status,hold_expires_at,sla_due_at,requested_at,created_by)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, id, code, key, requestHash, customerId, quote.pack().vendorId(), quote.pack().id(),
                        quote.pack().version(), request.eventDate(), request.startTime(), request.endTime(),
                        request.eventTimezone(), ts(time.startAt()), ts(time.endAt()), quote.pack().availabilityMode().name(),
                        capacitySlot, request.guests(), quote.venue().mode().name(), quote.venue().address(),
                        quote.venue().city(), upper(quote.venue().country()), quote.venue().lat(),
                        quote.venue().lng(), clean(request.notes()), quote.pack().title(), terms,
                        quote.price().currency(), quote.price().subtotalCents(), quote.price().addOnsCents(),
                        quote.price().travelFeeCents(), quote.price().discountCents(), quote.price().totalCents(),
                        quote.price().depositPct(), quote.price().cancellationPolicy(), initial.name(), ts(holdUntil),
                        ts(slaDue), instant ? null : ts(now), customerId);
                insertAddOns(id, quote);
                event(id, null, initial, BookingActor.CUSTOMER, customerId,
                        Map.of("bookingMode", quote.pack().bookingMode()));
                if (instant) createHold(id, customerId, quote.pack().vendorId(), request.eventDate(),
                        time, quote.pack().availabilityMode(), capacitySlot, holdUntil);
                else mail.sendBookingUpdate(email(quote.pack().vendorId()), code,
                        "New booking request", properties.publicWebUrl() + "/vendor/bookings/" + id);
            } catch (DataIntegrityViolationException ex) {
                throw unavailable(ex);
            }
            audit.record(customerId, "booking.created", "booking", id.toString(),
                    Map.of("status", initial.name(), "vendorId", quote.pack().vendorId().toString()));
            return get(customerId, id);
        });
    }

    @Transactional(readOnly = true)
    public BookingList customerBookings(UUID customerId, BookingStatus status, String cursor, int limit) {
        int pageSize = pageSize(limit);
        CustomerBookingCursor after = decodeCustomerCursor(cursor);
        String sql = "SELECT " + BOOKING_COLUMNS + " FROM bookings b JOIN packages p ON p.id=b.package_id "
                + "JOIN vendor_profiles vp ON vp.account_id=b.vendor_id WHERE b.customer_id=? AND b.deleted_at IS NULL"
                + (status == null ? "" : " AND b.status=?")
                + (after == null ? "" : " AND (b.created_at<? OR (b.created_at=? AND b.id<?))")
                + " ORDER BY b.created_at DESC,b.id DESC LIMIT ?";
        List<Object> params = new ArrayList<>();
        params.add(customerId);
        if (status != null) params.add(status.name());
        if (after != null) {
            params.add(ts(after.createdAt()));
            params.add(ts(after.createdAt()));
            params.add(after.id());
        }
        params.add(pageSize + 1);
        List<BookingRow> rows = jdbc.query(sql, this::mapRow, params.toArray());
        boolean hasNext = rows.size() > pageSize;
        List<BookingRow> page = hasNext ? rows.subList(0, pageSize) : rows;
        String nextCursor = hasNext ? encodeCustomerCursor(page.getLast()) : null;
        return new BookingList(page.stream().map(row -> detail(row, BookingActor.CUSTOMER)).toList(),
                nextCursor, Instant.now());
    }

    @Transactional(readOnly = true)
    public BookingList vendorBookings(UUID vendorId, BookingStatus status, String cursor, int limit) {
        int pageSize = pageSize(limit);
        VendorBookingCursor after = decodeVendorCursor(cursor);
        String sql = "SELECT " + BOOKING_COLUMNS + " FROM bookings b JOIN packages p ON p.id=b.package_id "
                + "JOIN vendor_profiles vp ON vp.account_id=b.vendor_id WHERE b.vendor_id=? AND b.deleted_at IS NULL"
                + (status == null ? "" : " AND b.status=?")
                + vendorCursorPredicate(after)
                + " ORDER BY " + VENDOR_BOOKING_PRIORITY + "," + VENDOR_BOOKING_DEADLINE
                + ",b.created_at DESC,b.id DESC LIMIT ?";
        List<Object> params = new ArrayList<>();
        params.add(vendorId);
        if (status != null) params.add(status.name());
        addVendorCursorParams(params, after);
        params.add(pageSize + 1);
        List<BookingRow> rows = jdbc.query(sql, this::mapRow, params.toArray());
        boolean hasNext = rows.size() > pageSize;
        List<BookingRow> page = hasNext ? rows.subList(0, pageSize) : rows;
        String nextCursor = hasNext ? encodeVendorCursor(page.getLast()) : null;
        return new BookingList(page.stream().map(row -> detail(row, BookingActor.VENDOR)).toList(),
                nextCursor, Instant.now());
    }

    @Transactional(readOnly = true)
    public Booking get(UUID accountId, UUID bookingId) {
        BookingRow row = requireBooking(bookingId);
        return detail(row, actor(accountId, row));
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Booking checkout(UUID customerId, UUID id, BookingVersionRequest request) {
        requireBookingsEnabled();
        BookingRow row = requireCustomer(customerId, id);
        if (row.status() != BookingStatus.DRAFT) stateMachine.require(row.status(), BookingStatus.PENDING_PAYMENT);
        requireVersion(row, request.version());
        requireLiveHold(row);
        availability.requireNotBlackedOut(row.vendorId(), row.eventDate());
        transition(row, BookingStatus.PENDING_PAYMENT, BookingActor.CUSTOMER, customerId,
                Map.of("handoff", "PHASE_5_PAYMENT"), "payment_due_at=hold_expires_at");
        return get(customerId, id);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Booking confirmPaymentStub(UUID customerId, UUID id, BookingVersionRequest request) {
        requireBookingsEnabled();
        if (!properties.booking().paymentStubEnabled()) {
            throw ApiException.conflict("BOOKING_PAYMENT_REQUIRED", "Payment confirmation is handled by Stripe");
        }
        BookingRow row = requireCustomer(customerId, id);
        requireVersion(row, request.version());
        if (row.status() != BookingStatus.DRAFT && row.status() != BookingStatus.PENDING_PAYMENT) {
            stateMachine.require(row.status(), BookingStatus.CONFIRMED);
        }
        requireLiveHold(row);
        availability.requireNotBlackedOut(row.vendorId(), row.eventDate());
        long stubPaidCents = ceilPercent(row.totalCents(), row.depositPct());
        transition(row, BookingStatus.CONFIRMED, BookingActor.SYSTEM, null,
                Map.of("payment", "NON_PRODUCTION_DEPOSIT_STUB", "paidCents", stubPaidCents),
                "confirmed_at=now(),payment_due_at=NULL,hold_expires_at=NULL,paid_cents=" + stubPaidCents);
        releaseHold(row);
        audit.record(customerId, "booking.confirmed_stub", "booking", id.toString(), Map.of());
        return get(customerId, id);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Booking accept(UUID vendorId, UUID id, BookingAcceptRequest request) {
        requireBookingsEnabled();
        BookingRow row = requireVendor(vendorId, id);
        requireVersion(row, request.version());
        stateMachine.require(row.status(), BookingStatus.VENDOR_ACCEPTED);
        requireRequestSlaLive(row);
        return withCapacityLock(row.vendorId(), row.eventDate(), () -> {
            int capacitySlot = availability.requireBookable(row.vendorId(), row.eventDate(),
                    row.startTime(), row.endTime(), row.availabilityMode(), null);
            long travel = request.travelFeeCents() == null ? row.travelFeeCents() : request.travelFeeCents();
            long addOns = row.addOnsCents();
            long total;
            try {
                if (request.addOns() != null) addOns = replaceAcceptedAddOns(id, request.addOns());
                total = Math.subtractExact(
                        Math.addExact(Math.addExact(row.subtotalCents(), addOns), travel),
                        row.discountCents());
            } catch (ArithmeticException ex) {
                throw ApiException.badRequest("BOOKING_PRICE_OVERFLOW", "Adjusted price is too large");
            }
            int changed = jdbc.update("""
                    UPDATE bookings SET status='VENDOR_ACCEPTED',addons_cents=?,travel_fee_cents=?,total_cents=?,
                      capacity_slot=?,accepted_at=now(),version=version+1,updated_at=now()
                    WHERE id=? AND status='REQUESTED' AND version=?
                    """, addOns, travel, total, capacitySlot, id, row.version());
            if (changed == 0) throw stale();
            event(id, BookingStatus.REQUESTED, BookingStatus.VENDOR_ACCEPTED,
                    BookingActor.VENDOR, vendorId,
                    Map.of("addOnsCents", addOns, "travelFeeCents", travel));
            Instant expires = Instant.now().plus(PAYMENT_WINDOW);
            int second = jdbc.update("""
                    UPDATE bookings SET status='PENDING_PAYMENT',payment_due_at=?,hold_expires_at=?,
                      version=version+1,updated_at=now() WHERE id=? AND status='VENDOR_ACCEPTED' AND version=?
                    """, ts(expires), ts(expires), id, row.version() + 1);
            if (second == 0) throw stale();
            event(id, BookingStatus.VENDOR_ACCEPTED, BookingStatus.PENDING_PAYMENT,
                    BookingActor.SYSTEM, null, Map.of("windowHours", 24));
            EventTime time = new EventTime(row.eventStartAt(), row.eventEndAt(),
                    row.eventDate(), row.eventTimezone());
            createHold(id, row.customerId(), row.vendorId(), row.eventDate(), time,
                    row.availabilityMode(), capacitySlot, expires);
            audit.record(vendorId, "booking.accepted", "booking", id.toString(), Map.of());
            mail.sendBookingUpdate(email(row.customerId()), row.code(), "Your request was accepted",
                    properties.publicWebUrl() + "/bookings/" + id);
            return get(vendorId, id);
        });
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Booking decline(UUID vendorId, UUID id, BookingDeclineRequest request) {
        requireBookingsEnabled();
        BookingRow row = requireVendor(vendorId, id);
        requireVersion(row, request.version());
        stateMachine.require(row.status(), BookingStatus.DECLINED);
        requireRequestSlaLive(row);
        transition(row, BookingStatus.DECLINED, BookingActor.VENDOR, vendorId,
                Map.of("reason", request.reason().name(), "message", nullToEmpty(request.message())),
                "cancel_reason=" + sqlLiteral(request.reason().name() + ": " + nullToEmpty(request.message())));
        audit.record(vendorId, "booking.declined", "booking", id.toString(), Map.of("reason", request.reason().name()));
        mail.sendBookingUpdate(email(row.customerId()), row.code(), "Your booking request was declined",
                properties.publicWebUrl() + "/bookings/" + id);
        return get(vendorId, id);
    }

    @Transactional
    public RefundCalculation refundPreview(UUID accountId, UUID id) {
        BookingRow row = requireBooking(id);
        BookingActor actor = actor(accountId, row);
        stateMachine.require(row.status(), actor == BookingActor.VENDOR
                ? BookingStatus.CANCELLED_BY_VENDOR : BookingStatus.CANCELLED_BY_CUSTOMER);
        RefundCalculation calculation = cancellationPolicies.calculate(id, row.cancellationPolicy(), row.eventDate(),
                row.eventTimezone(), row.paidCents(), actor == BookingActor.VENDOR);
        jdbc.update("UPDATE refund_calculations SET status='VOID' WHERE booking_id=? AND status='PREVIEWED'",
                id);
        insertRefundCalculation(calculation, row.paidCents(), "PREVIEWED");
        return calculation;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Booking cancel(UUID accountId, UUID id, BookingCancelRequest request) {
        requireBookingsEnabled();
        BookingRow row = requireBooking(id);
        BookingActor actor = actor(accountId, row);
        requireVersion(row, request.version());
        BookingStatus target = actor == BookingActor.VENDOR
                ? BookingStatus.CANCELLED_BY_VENDOR : BookingStatus.CANCELLED_BY_CUSTOMER;
        stateMachine.require(row.status(), target);
        RefundCalculation calculation;
        boolean storedPreview = request.refundCalculationId() != null;
        if (storedPreview) {
            calculation = requireRefundPreview(row, request.refundCalculationId(),
                    actor == BookingActor.VENDOR);
        } else if (actor == BookingActor.CUSTOMER) {
            throw ApiException.conflict("BOOKING_REFUND_PREVIEW_REQUIRED",
                    "Review the current refund amount before confirming cancellation");
        } else {
            calculation = cancellationPolicies.calculate(id, row.cancellationPolicy(), row.eventDate(),
                    row.eventTimezone(), row.paidCents(), true);
        }
        transition(row, target, actor, accountId,
                Map.of("refundPercent", calculation.refundPercent(),
                        "refundableCents", calculation.refundableCents()),
                "cancelled_at=now(),cancel_reason=" + sqlLiteral(request.reason())
                        + ",vendor_penalty_flag=" + (actor == BookingActor.VENDOR));
        if (storedPreview) {
            int finalized = jdbc.update("""
                    UPDATE refund_calculations SET status='FINAL',finalized_at=now()
                    WHERE id=? AND booking_id=? AND status='PREVIEWED'
                    """, calculation.calculationId(), id);
            if (finalized == 0) throw ApiException.conflict("BOOKING_REFUND_PREVIEW_EXPIRED",
                    "Refund preview expired; review the latest amount and try again");
        } else {
            insertRefundCalculation(calculation, row.paidCents(), "FINAL");
        }
        releaseHold(row);
        audit.record(accountId, "booking.cancelled", "booking", id.toString(),
                Map.of("actor", actor.name(), "refundPercent", calculation.refundPercent()));
        return get(accountId, id);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Booking complete(UUID vendorId, UUID id, BookingVersionRequest request) {
        requireBookingsEnabled();
        BookingRow row = requireVendor(vendorId, id);
        requireVersion(row, request.version());
        if (Instant.now().isBefore(row.eventEndAt())) {
            throw ApiException.conflict("BOOKING_EVENT_NOT_ENDED", "The booking cannot be completed before its scheduled end");
        }
        if (row.status() == BookingStatus.CONFIRMED) {
            transition(row, BookingStatus.IN_PROGRESS, BookingActor.SYSTEM, null,
                    Map.of("reason", "VENDOR_COMPLETION"), null);
            row = requireBooking(id);
        }
        stateMachine.require(row.status(), BookingStatus.COMPLETED);
        transition(row, BookingStatus.COMPLETED, BookingActor.VENDOR, vendorId, Map.of(),
                "completed_at=now(),dispute_window_ends_at=now()+interval '48 hours'");
        return get(vendorId, id);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Booking dispute(UUID customerId, UUID id, BookingDisputeRequest request) {
        requireBookingsEnabled();
        BookingRow row = requireCustomer(customerId, id);
        requireVersion(row, request.version());
        stateMachine.require(row.status(), BookingStatus.DISPUTED);
        if (row.disputeWindowEndsAt() == null || !Instant.now().isBefore(row.disputeWindowEndsAt())) {
            throw ApiException.conflict("BOOKING_DISPUTE_WINDOW_CLOSED", "The 48-hour dispute window has closed");
        }
        transition(row, BookingStatus.DISPUTED, BookingActor.CUSTOMER, customerId,
                Map.of("reason", request.reason()), null);
        audit.record(customerId, "booking.disputed", "booking", id.toString(), Map.of());
        return get(customerId, id);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Booking proposeReschedule(UUID accountId, UUID id, RescheduleProposalRequest request) {
        requireBookingsEnabled();
        BookingRow row = requireBooking(id);
        BookingActor actor = actor(accountId, row);
        requireVersion(row, request.version());
        if (row.status() != BookingStatus.CONFIRMED || row.rescheduleCount() > 0) {
            throw ApiException.conflict("BOOKING_RESCHEDULE_NOT_ALLOWED", "This booking cannot be rescheduled");
        }
        if (Duration.between(Instant.now(), row.eventStartAt()).toDays() < 14) {
            throw ApiException.conflict("BOOKING_RESCHEDULE_TOO_LATE", "Rescheduling requires at least 14 days' notice");
        }
        requireEventTimezone(request.eventTimezone(), row.eventTimezone());
        EventTime time = eventTime(request.eventDate(), request.startTime(), request.endTime(),
                request.eventTimezone(), row.availabilityMode());
        requireSameDayAllowed(time, row.allowSameDay());
        // A proposal does not reserve the date; final mutual confirmation rechecks atomically.
        UUID proposalId = Uuidv7.next();
        try {
            jdbc.update("""
                    INSERT INTO booking_reschedules(id,booking_id,proposed_by,event_date,start_time,end_time,
                      event_timezone,customer_approved,vendor_approved)
                    VALUES (?,?,?,?,?,?,?,?,?)
                    """, proposalId, id, accountId, request.eventDate(), request.startTime(), request.endTime(),
                    request.eventTimezone(), actor == BookingActor.CUSTOMER, actor == BookingActor.VENDOR);
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.conflict("BOOKING_RESCHEDULE_PENDING", "A reschedule proposal is already pending");
        }
        historyEvent(row, actor, accountId, "RESCHEDULE_PROPOSED",
                Map.of("proposalId", proposalId.toString(), "eventDate", request.eventDate().toString()));
        return get(accountId, id);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Booking decideReschedule(UUID accountId, UUID id, RescheduleDecisionRequest request) {
        requireBookingsEnabled();
        BookingRow row = requireBooking(id);
        BookingActor actor = actor(accountId, row);
        requireVersion(row, request.version());
        RescheduleRow proposal = pendingRescheduleRow(id);
        if (proposal == null) throw ApiException.notFound("BOOKING_RESCHEDULE_NOT_FOUND", "No reschedule proposal is pending");
        if (!request.accept()) {
            jdbc.update("UPDATE booking_reschedules SET status='REJECTED',resolved_at=now() WHERE id=? AND status='PENDING'", proposal.id());
            historyEvent(row, actor, accountId, "RESCHEDULE_REJECTED", Map.of("proposalId", proposal.id().toString()));
            return get(accountId, id);
        }
        if (row.status() != BookingStatus.CONFIRMED || row.rescheduleCount() > 0) {
            throw ApiException.conflict("BOOKING_RESCHEDULE_NOT_ALLOWED", "This booking cannot be rescheduled");
        }
        if (Duration.between(Instant.now(), row.eventStartAt()).toDays() < 14) {
            throw ApiException.conflict("BOOKING_RESCHEDULE_TOO_LATE",
                    "Rescheduling requires at least 14 days' notice");
        }
        requireEventTimezone(proposal.eventTimezone(), row.eventTimezone());
        EventTime proposedTime = eventTime(proposal.eventDate(), proposal.startTime(), proposal.endTime(),
                proposal.eventTimezone(), row.availabilityMode());
        requireSameDayAllowed(proposedTime, row.allowSameDay());
        boolean customer = proposal.customerApproved() || actor == BookingActor.CUSTOMER;
        boolean vendor = proposal.vendorApproved() || actor == BookingActor.VENDOR;
        jdbc.update("UPDATE booking_reschedules SET customer_approved=?,vendor_approved=? WHERE id=? AND status='PENDING'",
                customer, vendor, proposal.id());
        if (!customer || !vendor) {
            historyEvent(row, actor, accountId, "RESCHEDULE_APPROVED", Map.of("proposalId", proposal.id().toString()));
            return get(accountId, id);
        }
        return withCapacityLock(row.vendorId(), proposal.eventDate(), () -> {
            int capacity = availability.requireBookable(row.vendorId(), proposal.eventDate(),
                    proposal.startTime(), proposal.endTime(), row.availabilityMode(), row.id());
            try {
                int changed = jdbc.update("""
                        UPDATE bookings SET event_date=?,start_time=?,end_time=?,event_timezone=?,
                          event_start_at=?,event_end_at=?,capacity_slot=?,reschedule_count=1,
                          version=version+1,updated_at=now()
                        WHERE id=? AND status='CONFIRMED' AND version=? AND reschedule_count=0
                        """, proposal.eventDate(), proposal.startTime(), proposal.endTime(),
                        proposal.eventTimezone(), ts(proposedTime.startAt()), ts(proposedTime.endAt()),
                        capacity, id, row.version());
                if (changed == 0) throw stale();
            } catch (DataIntegrityViolationException ex) { throw unavailable(ex); }
            jdbc.update("UPDATE booking_reschedules SET status='ACCEPTED',resolved_at=now(),customer_approved=true,vendor_approved=true WHERE id=?", proposal.id());
            event(id, row.status(), row.status(), actor, accountId,
                    Map.of("eventType", "RESCHEDULE_ACCEPTED", "oldDate", row.eventDate().toString(),
                            "newDate", proposal.eventDate().toString()));
            return get(accountId, id);
        });
    }

    @Scheduled(fixedDelayString = "${wedjan.booking.scheduler-interval-ms:60000}",
            initialDelayString = "${wedjan.booking.scheduler-initial-delay-ms:30000}")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void automateTransitions() {
        expireWhere("status='REQUESTED' AND sla_due_at<=now()", "REQUEST_SLA_EXPIRED");
        expireWhere("status IN ('DRAFT','PENDING_PAYMENT') AND hold_expires_at<=now()", "HOLD_OR_PAYMENT_EXPIRED");
        jdbc.query("SELECT id FROM bookings WHERE status='CONFIRMED' AND event_start_at<=now() FOR UPDATE SKIP LOCKED LIMIT 100",
                rs -> { while (rs.next()) autoTransition(rs.getObject(1, UUID.class), BookingStatus.CONFIRMED,
                        BookingStatus.IN_PROGRESS, "EVENT_STARTED"); return null; });
        jdbc.query("SELECT id FROM bookings WHERE status='IN_PROGRESS' AND event_end_at+interval '24 hours'<=now() FOR UPDATE SKIP LOCKED LIMIT 100",
                rs -> { while (rs.next()) autoComplete(rs.getObject(1, UUID.class)); return null; });
        jdbc.update("UPDATE booking_holds SET released_at=now() WHERE released_at IS NULL AND expires_at<=now()");
    }

    private void expireWhere(String predicate, String reason) {
        jdbc.query("SELECT id,status,code,customer_id,vendor_id FROM bookings WHERE " + predicate + " FOR UPDATE SKIP LOCKED LIMIT 100", rs -> {
            while (rs.next()) {
                UUID id = rs.getObject("id", UUID.class);
                BookingStatus from = BookingStatus.valueOf(rs.getString("status"));
                int changed = jdbc.update("UPDATE bookings SET status='EXPIRED',version=version+1,updated_at=now() WHERE id=? AND status=?",
                        id, from.name());
                if (changed == 1) {
                    event(id, from, BookingStatus.EXPIRED, BookingActor.SYSTEM, null, Map.of("reason", reason));
                    jdbc.update("UPDATE booking_holds SET released_at=now() WHERE booking_id=? AND released_at IS NULL", id);
                    String code = rs.getString("code");
                    mail.sendBookingUpdate(email(rs.getObject("customer_id", UUID.class)), code,
                            "Booking window expired", properties.publicWebUrl() + "/bookings/" + id);
                    mail.sendBookingUpdate(email(rs.getObject("vendor_id", UUID.class)), code,
                            "Booking window expired", properties.publicWebUrl() + "/vendor/bookings/" + id);
                }
            }
            return null;
        });
    }

    private void autoTransition(UUID id, BookingStatus from, BookingStatus to, String reason) {
        int changed = jdbc.update("UPDATE bookings SET status=?,version=version+1,updated_at=now() WHERE id=? AND status=?",
                to.name(), id, from.name());
        if (changed == 1) event(id, from, to, BookingActor.SYSTEM, null, Map.of("reason", reason));
    }

    private void autoComplete(UUID id) {
        int changed = jdbc.update("""
                UPDATE bookings SET status='COMPLETED',completed_at=now(),
                  dispute_window_ends_at=now()+interval '48 hours',version=version+1,updated_at=now()
                WHERE id=? AND status='IN_PROGRESS'
                """, id);
        if (changed == 1) event(id, BookingStatus.IN_PROGRESS, BookingStatus.COMPLETED,
                BookingActor.SYSTEM, null, Map.of("reason", "EVENT_ENDED_24H"));
    }

    private Booking detail(BookingRow row, BookingActor viewer) {
        List<BookingDtos.BookingAddOn> addOns = jdbc.query("""
                SELECT add_on_id,title_snap,qty,price_cents_snap,total_cents_snap
                FROM booking_add_ons WHERE booking_id=? ORDER BY title_snap,add_on_id
                """, (rs, index) -> new BookingDtos.BookingAddOn(rs.getObject("add_on_id", UUID.class),
                rs.getString("title_snap"), rs.getInt("qty"), rs.getLong("price_cents_snap"),
                rs.getLong("total_cents_snap")), row.id());
        List<BookingEvent> events = jdbc.query("""
                SELECT id,from_status,to_status,actor,actor_id,metadata::text,created_at
                FROM booking_events WHERE booking_id=? ORDER BY created_at,id
                """, this::mapEvent, row.id());
        Reschedule pending = pendingReschedule(row.id());
        PriceBreakdown price = new PriceBreakdown(row.currency(), row.subtotalCents(), row.addOnsCents(),
                row.travelFeeCents(), row.discountCents(), row.totalCents(), row.depositPct(),
                ceilPercent(row.totalCents(), row.depositPct()), row.cancellationPolicy());
        Instant now = Instant.now();
        return new Booking(row.id(), row.code(), row.customerId(), row.vendorId(), row.vendorSlug(),
                row.vendorName(), row.packageId(), row.packageTitle(), row.bookingMode(), row.eventDate(),
                row.startTime(), row.endTime(), row.eventTimezone(), row.guests(), row.venueLocationMode(),
                row.venueAddress(), row.venueCity(), row.venueCountry(), row.venueLat(), row.venueLng(), row.notes(),
                price, addOns, row.status(),
                row.holdExpiresAt(), row.slaDueAt(), row.paymentDueAt(), row.confirmedAt(),
                row.completedAt(), row.disputeWindowEndsAt(), row.cancelReason(), row.vendorPenaltyFlag(),
                row.version(), row.createdAt(), now,
                stateMachine.allowedActions(row.status(), viewer, now, row.holdExpiresAt(),
                        row.slaDueAt(), row.eventStartAt(), row.eventEndAt(),
                        row.disputeWindowEndsAt(), pending != null, row.rescheduleCount()), events, pending);
    }

    private BookingRow requireBooking(UUID id) {
        List<BookingRow> rows = jdbc.query("SELECT " + BOOKING_COLUMNS
                + " FROM bookings b JOIN packages p ON p.id=b.package_id JOIN vendor_profiles vp ON vp.account_id=b.vendor_id"
                + " WHERE b.id=? AND b.deleted_at IS NULL", this::mapRow, id);
        if (rows.isEmpty()) throw ApiException.notFound("BOOKING_NOT_FOUND", "Booking not found");
        return rows.getFirst();
    }

    private BookingRow requireCustomer(UUID customerId, UUID id) {
        BookingRow row = requireBooking(id);
        if (!row.customerId().equals(customerId)) throw ApiException.notFound("BOOKING_NOT_FOUND", "Booking not found");
        return row;
    }

    private BookingRow requireVendor(UUID vendorId, UUID id) {
        BookingRow row = requireBooking(id);
        if (!row.vendorId().equals(vendorId)) throw ApiException.notFound("BOOKING_NOT_FOUND", "Booking not found");
        return row;
    }

    private BookingRow findByIdempotency(UUID customerId, String key) {
        List<BookingRow> rows = jdbc.query("SELECT " + BOOKING_COLUMNS
                + " FROM bookings b JOIN packages p ON p.id=b.package_id JOIN vendor_profiles vp ON vp.account_id=b.vendor_id"
                + " WHERE b.customer_id=? AND b.idempotency_key=? AND b.deleted_at IS NULL",
                this::mapRow, customerId, key);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private BookingActor actor(UUID accountId, BookingRow row) {
        if (row.customerId().equals(accountId)) return BookingActor.CUSTOMER;
        if (row.vendorId().equals(accountId)) return BookingActor.VENDOR;
        throw ApiException.notFound("BOOKING_NOT_FOUND", "Booking not found");
    }

    private void transition(BookingRow row, BookingStatus to, BookingActor actor, UUID actorId,
            Map<String, Object> metadata, String extraSet) {
        stateMachine.require(row.status(), to);
        String extras = extraSet == null || extraSet.isBlank() ? "" : "," + extraSet;
        int changed = jdbc.update("UPDATE bookings SET status=?,version=version+1,updated_at=now()" + extras
                + " WHERE id=? AND status=? AND version=?", to.name(), row.id(), row.status().name(), row.version());
        if (changed == 0) throw stale();
        event(row.id(), row.status(), to, actor, actorId, metadata);
    }

    private void historyEvent(BookingRow row, BookingActor actor, UUID actorId, String type,
            Map<String, Object> metadata) {
        int changed = jdbc.update("UPDATE bookings SET version=version+1,updated_at=now() WHERE id=? AND version=?",
                row.id(), row.version());
        if (changed == 0) throw stale();
        var values = new java.util.LinkedHashMap<String, Object>(metadata);
        values.put("eventType", type);
        event(row.id(), row.status(), row.status(), actor, actorId, values);
    }

    private void event(UUID id, BookingStatus from, BookingStatus to, BookingActor actor,
            UUID actorId, Map<String, Object> metadata) {
        jdbc.update("""
                INSERT INTO booking_events(id,booking_id,from_status,to_status,actor,actor_id,event_type,metadata)
                VALUES (?,?,?,?,?,?,?,?::jsonb)
                """, Uuidv7.next(), id, from == null ? null : from.name(), to.name(), actor.name(), actorId,
                metadata.containsKey("eventType") ? metadata.get("eventType").toString() : "STATUS_TRANSITION",
                json(metadata));
    }

    private void createHold(UUID bookingId, UUID customerId, UUID vendorId, LocalDate eventDate,
            EventTime time, AvailabilityMode mode, int capacitySlot, Instant expiresAt) {
        String token = UUID.randomUUID().toString();
        try {
            jdbc.update("""
                    INSERT INTO booking_holds(id,booking_id,vendor_id,customer_id,event_date,event_start_at,
                      event_end_at,availability_mode_snap,capacity_slot,token_hash,expires_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?)
                    """, Uuidv7.next(), bookingId, vendorId, customerId, eventDate, ts(time.startAt()), ts(time.endAt()),
                    mode.name(), capacitySlot, Sha256.hex(token), ts(expiresAt));
            redis.opsForValue().set(holdCacheKey(vendorId, eventDate, mode, capacitySlot,
                    time.startAt(), time.endAt()), bookingId.toString(), Duration.between(Instant.now(), expiresAt));
        } catch (DataIntegrityViolationException ex) {
            throw unavailable(ex);
        } catch (RedisConnectionFailureException ex) {
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "BOOKING_HOLD_UNAVAILABLE", "Checkout is temporarily unavailable; please retry");
        }
    }

    private void releaseHold(BookingRow row) {
        jdbc.update("UPDATE booking_holds SET released_at=now() WHERE booking_id=? AND released_at IS NULL", row.id());
        try {
            redis.delete(holdCacheKey(row.vendorId(), row.eventDate(), row.availabilityMode(),
                    row.capacitySlot(), row.eventStartAt(), row.eventEndAt()));
        } catch (RuntimeException ex) { log.warn("Could not clear Redis hold for booking {}", row.id()); }
    }

    private void requireLiveHold(BookingRow row) {
        if (row.holdExpiresAt() == null || !Instant.now().isBefore(row.holdExpiresAt())) {
            throw ApiException.conflict("BOOKING_HOLD_EXPIRED", "The checkout hold expired; choose availability again");
        }
        Long count = jdbc.queryForObject("""
                SELECT count(*) FROM booking_holds WHERE booking_id=? AND released_at IS NULL AND expires_at>now()
                """, Long.class, row.id());
        if (count == null || count == 0) throw ApiException.conflict("BOOKING_HOLD_EXPIRED",
                "The checkout hold expired; choose availability again");
    }

    private <T> T withCapacityLock(UUID vendorId, LocalDate date, Supplier<T> action) {
        String key = "booking:lock:" + vendorId + ":" + date;
        String token = UUID.randomUUID().toString();
        boolean acquired;
        try {
            acquired = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, token, Duration.ofSeconds(10)));
        } catch (RuntimeException ex) {
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "BOOKING_HOLD_UNAVAILABLE", "Checkout is temporarily unavailable; please retry");
        }
        if (!acquired) throw ApiException.conflict("BOOKING_CHECKOUT_BUSY", "Another checkout is reserving this date; retry shortly");
        try {
            advisoryLock(key);
            return action.get();
        } finally {
            try { redis.execute(COMPARE_DELETE, List.of(key), token); }
            catch (RuntimeException ex) { log.warn("Could not release Redis capacity lock {}", key); }
        }
    }

    private void advisoryLock(String key) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> {}, key);
    }

    private void enforceCreateQuota(UUID customerId, UUID vendorId, boolean instant) {
        if (instant) {
            Long activeHolds = jdbc.queryForObject("""
                    SELECT count(*) FROM booking_holds
                    WHERE customer_id=? AND released_at IS NULL AND expires_at>now()
                    """, Long.class, customerId);
            Long vendorHolds = jdbc.queryForObject("""
                    SELECT count(*) FROM booking_holds
                    WHERE customer_id=? AND vendor_id=? AND released_at IS NULL AND expires_at>now()
                    """, Long.class, customerId, vendorId);
            if ((activeHolds != null && activeHolds >= 3)
                    || (vendorHolds != null && vendorHolds >= 1)) {
                throw ApiException.conflict("BOOKING_ACTIVE_HOLD_LIMIT",
                        "Finish or release an active checkout before holding another date");
            }
            return;
        }
        Long activeRequests = jdbc.queryForObject("""
                SELECT count(*) FROM bookings WHERE customer_id=? AND status='REQUESTED'
                  AND sla_due_at>now() AND deleted_at IS NULL
                """, Long.class, customerId);
        Long vendorRequests = jdbc.queryForObject("""
                SELECT count(*) FROM bookings WHERE customer_id=? AND vendor_id=? AND status='REQUESTED'
                  AND sla_due_at>now() AND deleted_at IS NULL
                """, Long.class, customerId, vendorId);
        if ((activeRequests != null && activeRequests >= 10)
                || (vendorRequests != null && vendorRequests >= 2)) {
            throw ApiException.conflict("BOOKING_ACTIVE_REQUEST_LIMIT",
                    "Wait for existing booking requests before sending another");
        }
    }

    private EventTime eventTime(BookingConfigurationRequest request, PackageSnapshot pack) {
        requireEventTimezone(request.eventTimezone(), pack.vendorTimezone());
        return eventTime(request.eventDate(), request.startTime(), request.endTime(),
                request.eventTimezone(), pack.availabilityMode());
    }

    private EventTime eventTime(LocalDate date, LocalTime start, LocalTime end, String timezone,
            AvailabilityMode mode) {
        ZoneId zone;
        try { zone = ZoneId.of(timezone); }
        catch (RuntimeException ex) { throw ApiException.badRequest("BOOKING_TIMEZONE_INVALID", "Invalid event timezone"); }
        if (mode == AvailabilityMode.SLOT) {
            if (start == null || end == null || !end.isAfter(start)) throw ApiException.badRequest(
                    "BOOKING_SLOT_REQUIRED", "Choose a valid start and end time");
            return new EventTime(date.atTime(start).atZone(zone).toInstant(),
                    date.atTime(end).atZone(zone).toInstant(), date, timezone);
        }
        return new EventTime(date.atStartOfDay(zone).toInstant(),
                date.plusDays(1).atStartOfDay(zone).toInstant(), date, timezone);
    }

    private static void requireSameDayAllowed(EventTime time, boolean allowSameDay) {
        LocalDate today = ZonedDateTime.now(ZoneId.of(time.timezone())).toLocalDate();
        if (time.date().isBefore(today)) throw ApiException.badRequest("BOOKING_DATE_PAST", "Event date cannot be in the past");
        if (time.date().equals(today) && !allowSameDay) throw ApiException.conflict(
                "BOOKING_SAME_DAY_DISABLED", "This package does not accept same-day bookings");
    }

    private static void requireEventTimezone(String requested, String authoritative) {
        if (!authoritative.equals(requested)) {
            throw ApiException.badRequest("BOOKING_TIMEZONE_MISMATCH",
                    "Use the vendor's venue-local timezone: " + authoritative);
        }
    }

    private void insertAddOns(UUID bookingId, CalculatedQuote quote) {
        for (var addOn : quote.addOns()) jdbc.update("""
                INSERT INTO booking_add_ons(booking_id,add_on_id,title_snap,qty,price_cents_snap,total_cents_snap)
                VALUES (?,?,?,?,?,?)
                """, bookingId, addOn.addOnId(), addOn.title(), addOn.qty(), addOn.unitPriceCents(), addOn.totalCents());
    }

    private long replaceAcceptedAddOns(UUID bookingId,
            List<BookingDtos.BookingAddOnRequest> selections) {
        Map<UUID, BookingDtos.BookingAddOn> available = jdbc.query("""
                SELECT add_on_id,title_snap,qty,price_cents_snap,total_cents_snap
                FROM booking_add_ons WHERE booking_id=?
                """, (rs, row) -> new BookingDtos.BookingAddOn(rs.getObject("add_on_id", UUID.class),
                rs.getString("title_snap"), rs.getInt("qty"), rs.getLong("price_cents_snap"),
                rs.getLong("total_cents_snap")), bookingId).stream().collect(
                java.util.stream.Collectors.toMap(BookingDtos.BookingAddOn::addOnId, item -> item));
        Set<UUID> seen = new HashSet<>();
        List<BookingDtos.BookingAddOn> adjusted = new ArrayList<>();
        long total = 0;
        for (var selection : selections) {
            if (!seen.add(selection.addOnId())) throw ApiException.badRequest(
                    "BOOKING_ADD_ON_DUPLICATE", "Select each add-on once and adjust its quantity");
            BookingDtos.BookingAddOn snapshot = available.get(selection.addOnId());
            if (snapshot == null) throw ApiException.badRequest("BOOKING_ADD_ON_ADJUSTMENT_INVALID",
                    "Vendors may adjust only add-ons selected in the original request");
            long line = Math.multiplyExact(snapshot.unitPriceCents(), selection.qty());
            total = Math.addExact(total, line);
            adjusted.add(new BookingDtos.BookingAddOn(snapshot.addOnId(), snapshot.title(),
                    selection.qty(), snapshot.unitPriceCents(), line));
        }
        jdbc.update("DELETE FROM booking_add_ons WHERE booking_id=?", bookingId);
        for (var addOn : adjusted) jdbc.update("""
                INSERT INTO booking_add_ons(booking_id,add_on_id,title_snap,qty,price_cents_snap,total_cents_snap)
                VALUES (?,?,?,?,?,?)
                """, bookingId, addOn.addOnId(), addOn.title(), addOn.qty(),
                addOn.unitPriceCents(), addOn.totalCents());
        return total;
    }

    private String packageTerms(PackageSnapshot pack) {
        return json(Map.of("descriptionMd", nullToEmpty(pack.descriptionMd()),
                "whatsIncludedMd", nullToEmpty(pack.whatsIncludedMd()),
                "whatsExcludedMd", nullToEmpty(pack.whatsExcludedMd()),
                "pricingModel", pack.pricingModel(), "bookingMode", pack.bookingMode(),
                "durationMinutes", pack.durationMinutes() == null ? 0 : pack.durationMinutes(),
                "allowSameDay", pack.allowSameDay(), "cancellationScheduleVersion", 1));
    }

    private String nextCode() {
        Long sequence = jdbc.queryForObject("SELECT nextval('booking_code_seq')", Long.class);
        return "BK-" + java.time.Year.now() + "-" + String.format("%06d", sequence);
    }

    private String email(UUID accountId) {
        return jdbc.queryForObject("SELECT email FROM accounts WHERE id=?", String.class, accountId);
    }

    private void insertRefundCalculation(RefundCalculation calculation, long basisCents, String status) {
        jdbc.update("""
                INSERT INTO refund_calculations(id,booking_id,policy_snap,days_to_event,basis_cents,
                  refund_percent,refundable_cents,non_refundable_cents,vendor_initiated,status,finalized_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,CASE WHEN ?='FINAL' THEN now() ELSE NULL END)
                """, calculation.calculationId(), calculation.bookingId(), calculation.policy(),
                calculation.daysToEvent(), basisCents, calculation.refundPercent(),
                calculation.refundableCents(), calculation.nonRefundableCents(),
                calculation.vendorPenalty(), status, status);
    }

    private RefundCalculation requireRefundPreview(BookingRow row, UUID calculationId,
            boolean vendorInitiated) {
        List<RefundCalculation> previews = jdbc.query("""
                SELECT id,booking_id,policy_snap,days_to_event,refund_percent,refundable_cents,
                  non_refundable_cents,vendor_initiated,created_at
                FROM refund_calculations
                WHERE id=? AND booking_id=? AND policy_snap=? AND basis_cents=?
                  AND vendor_initiated=? AND status='PREVIEWED'
                  AND created_at > now()-interval '15 minutes'
                """, (rs, index) -> new RefundCalculation(rs.getObject("id", UUID.class),
                rs.getObject("booking_id", UUID.class), rs.getString("policy_snap"),
                rs.getInt("days_to_event"), rs.getInt("refund_percent"),
                rs.getLong("refundable_cents"), rs.getLong("non_refundable_cents"),
                rs.getBoolean("vendor_initiated"), rs.getTimestamp("created_at").toInstant()),
                calculationId, row.id(), row.cancellationPolicy(), row.paidCents(), vendorInitiated);
        if (previews.isEmpty()) {
            throw ApiException.conflict("BOOKING_REFUND_PREVIEW_EXPIRED",
                    "Refund preview expired; review the latest amount and try again");
        }
        return previews.getFirst();
    }

    private Reschedule pendingReschedule(UUID bookingId) {
        RescheduleRow row = pendingRescheduleRow(bookingId);
        return row == null ? null : new Reschedule(row.id(), row.eventDate(), row.startTime(), row.endTime(),
                row.eventTimezone(), row.customerApproved(), row.vendorApproved(), row.status(), row.createdAt());
    }

    private RescheduleRow pendingRescheduleRow(UUID bookingId) {
        List<RescheduleRow> rows = jdbc.query("""
                SELECT id,event_date,start_time,end_time,event_timezone,customer_approved,
                  vendor_approved,status,created_at FROM booking_reschedules
                WHERE booking_id=? AND status='PENDING'
                """, (rs, index) -> new RescheduleRow(rs.getObject("id", UUID.class),
                rs.getObject("event_date", LocalDate.class), rs.getObject("start_time", LocalTime.class),
                rs.getObject("end_time", LocalTime.class), rs.getString("event_timezone"),
                rs.getBoolean("customer_approved"), rs.getBoolean("vendor_approved"), rs.getString("status"),
                rs.getTimestamp("created_at").toInstant()), bookingId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private BookingEvent mapEvent(ResultSet rs, int row) throws SQLException {
        return new BookingEvent(rs.getObject("id", UUID.class),
                rs.getString("from_status") == null ? null : BookingStatus.valueOf(rs.getString("from_status")),
                BookingStatus.valueOf(rs.getString("to_status")), BookingActor.valueOf(rs.getString("actor")),
                rs.getObject("actor_id", UUID.class), parseMap(rs.getString("metadata")),
                rs.getTimestamp("created_at").toInstant());
    }

    private static int pageSize(int requested) {
        if (requested < 1 || requested > 50) {
            throw ApiException.badRequest("BOOKING_PAGE_LIMIT_INVALID",
                    "Booking page limit must be between 1 and 50");
        }
        return requested;
    }

    private static String vendorCursorPredicate(VendorBookingCursor cursor) {
        if (cursor == null) return "";
        String laterCreated = "(b.created_at<? OR (b.created_at=? AND b.id<?))";
        if (cursor.deadline() == null) {
            return " AND (" + VENDOR_BOOKING_PRIORITY + ">? OR ("
                    + VENDOR_BOOKING_PRIORITY + "=? AND " + VENDOR_BOOKING_DEADLINE
                    + " IS NULL AND " + laterCreated + "))";
        }
        return " AND (" + VENDOR_BOOKING_PRIORITY + ">? OR ("
                + VENDOR_BOOKING_PRIORITY + "=? AND (" + VENDOR_BOOKING_DEADLINE
                + " IS NULL OR " + VENDOR_BOOKING_DEADLINE + ">? OR ("
                + VENDOR_BOOKING_DEADLINE + "=? AND " + laterCreated + "))))";
    }

    private static void addVendorCursorParams(List<Object> params, VendorBookingCursor cursor) {
        if (cursor == null) return;
        params.add(cursor.priority());
        params.add(cursor.priority());
        if (cursor.deadline() != null) {
            params.add(ts(cursor.deadline()));
            params.add(ts(cursor.deadline()));
        }
        params.add(ts(cursor.createdAt()));
        params.add(ts(cursor.createdAt()));
        params.add(cursor.id());
    }

    private static String encodeCustomerCursor(BookingRow row) {
        return encodeCursor("customer|" + row.createdAt() + "|" + row.id());
    }

    private static String encodeVendorCursor(BookingRow row) {
        Instant deadline = bookingDeadline(row);
        return encodeCursor("vendor|" + vendorPriority(row.status()) + "|"
                + (deadline == null ? "" : deadline) + "|" + row.createdAt() + "|" + row.id());
    }

    private static CustomerBookingCursor decodeCustomerCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            String[] values = decodeCursor(cursor).split("\\|", -1);
            if (values.length != 3 || !"customer".equals(values[0])) throw invalidBookingCursor();
            return new CustomerBookingCursor(Instant.parse(values[1]), UUID.fromString(values[2]));
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw invalidBookingCursor();
        }
    }

    private static VendorBookingCursor decodeVendorCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            String[] values = decodeCursor(cursor).split("\\|", -1);
            if (values.length != 5 || !"vendor".equals(values[0])) throw invalidBookingCursor();
            int priority = Integer.parseInt(values[1]);
            if (priority < 0 || priority > 2) throw invalidBookingCursor();
            Instant deadline = values[2].isEmpty() ? null : Instant.parse(values[2]);
            return new VendorBookingCursor(priority, deadline, Instant.parse(values[3]),
                    UUID.fromString(values[4]));
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw invalidBookingCursor();
        }
    }

    private static String encodeCursor(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeCursor(String cursor) {
        if (cursor.length() > 512) throw invalidBookingCursor();
        return new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
    }

    private static int vendorPriority(BookingStatus status) {
        return switch (status) {
            case REQUESTED -> 0;
            case PENDING_PAYMENT -> 1;
            default -> 2;
        };
    }

    private static Instant bookingDeadline(BookingRow row) {
        if (row.slaDueAt() != null) return row.slaDueAt();
        if (row.paymentDueAt() != null) return row.paymentDueAt();
        return row.eventStartAt();
    }

    private static ApiException invalidBookingCursor() {
        return ApiException.badRequest("BOOKING_CURSOR_INVALID", "Invalid booking cursor");
    }

    private BookingRow mapRow(ResultSet rs, int index) throws SQLException {
        return new BookingRow(rs.getObject("id", UUID.class), rs.getString("code"),
                rs.getString("idempotency_request_hash"), rs.getObject("customer_id", UUID.class),
                rs.getObject("vendor_id", UUID.class), rs.getString("vendor_slug"), rs.getString("vendor_name"),
                rs.getObject("package_id", UUID.class), rs.getString("package_title_snap"),
                rs.getString("booking_mode_snap"), rs.getObject("event_date", LocalDate.class),
                rs.getObject("start_time", LocalTime.class), rs.getObject("end_time", LocalTime.class),
                rs.getString("event_timezone"), rs.getTimestamp("event_start_at").toInstant(),
                rs.getTimestamp("event_end_at").toInstant(), AvailabilityMode.valueOf(rs.getString("availability_mode_snap")),
                rs.getInt("capacity_slot"), (Integer) rs.getObject("guests"),
                VenueLocationMode.valueOf(rs.getString("venue_location_mode")), rs.getString("venue_address"),
                rs.getString("venue_city"), rs.getString("venue_country"), nullableDouble(rs, "venue_lat"),
                nullableDouble(rs, "venue_lng"), rs.getString("notes"),
                rs.getString("currency"), rs.getLong("subtotal_cents"), rs.getLong("addons_cents"),
                rs.getLong("travel_fee_cents"), rs.getLong("discount_cents"), rs.getLong("total_cents"),
                rs.getLong("paid_cents"), rs.getInt("deposit_pct"), rs.getString("cancellation_policy_snap"),
                BookingStatus.valueOf(rs.getString("status")), instant(rs, "hold_expires_at"),
                instant(rs, "sla_due_at"), instant(rs, "payment_due_at"), instant(rs, "confirmed_at"),
                instant(rs, "completed_at"), instant(rs, "dispute_window_ends_at"), rs.getString("cancel_reason"),
                rs.getBoolean("vendor_penalty_flag"), rs.getBoolean("allow_same_day_snap"),
                rs.getInt("reschedule_count"), rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant());
    }

    private static final String BOOKING_COLUMNS = """
            b.id,b.code,b.idempotency_request_hash,b.customer_id,b.vendor_id,vp.slug vendor_slug,
            vp.business_name vendor_name,b.package_id,b.package_title_snap,
            COALESCE(b.package_terms_snap->>'bookingMode',p.booking_mode) booking_mode_snap,
            COALESCE((b.package_terms_snap->>'allowSameDay')::boolean,p.allow_same_day) allow_same_day_snap,
            b.event_date,b.start_time,b.end_time,b.event_timezone,b.event_start_at,b.event_end_at,
            b.availability_mode_snap,b.capacity_slot,b.guests,b.venue_location_mode,b.venue_address,b.venue_city,b.venue_country,
            b.venue_lat,b.venue_lng,b.notes,
            b.currency,b.subtotal_cents,b.addons_cents,b.travel_fee_cents,b.discount_cents,b.total_cents,b.paid_cents,
            b.deposit_pct,b.cancellation_policy_snap,b.status,b.hold_expires_at,b.sla_due_at,b.payment_due_at,
            b.confirmed_at,b.completed_at,b.dispute_window_ends_at,b.cancel_reason,b.vendor_penalty_flag,
            b.reschedule_count,b.version,b.created_at
            """;

    private static Instant instant(ResultSet rs, String name) throws SQLException {
        Timestamp value = rs.getTimestamp(name);
        return value == null ? null : value.toInstant();
    }
    private static Timestamp ts(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static Double nullableDouble(ResultSet rs, String name) throws SQLException {
        double value = rs.getDouble(name); return rs.wasNull() ? null : value;
    }
    private static long ceilPercent(long cents, int pct) {
        long whole = Math.multiplyExact(Math.floorDiv(cents, 100), pct);
        long remainder = Math.floorDiv(
                Math.addExact(Math.multiplyExact(Math.floorMod(cents, 100), pct), 99), 100);
        return Math.addExact(whole, remainder);
    }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String upper(String value) {
        String cleaned = clean(value);
        return cleaned == null ? null : cleaned.toUpperCase(java.util.Locale.ROOT);
    }
    private static String nullToEmpty(String value) { return value == null ? "" : value; }
    private static void requireNotSelfBooking(UUID customerId, UUID vendorId) {
        if (customerId != null && customerId.equals(vendorId)) throw ApiException.conflict("BOOKING_SELF_NOT_ALLOWED", "You cannot book your own vendor listing");
    }
    private static void requireVersion(BookingRow row, long version) {
        if (row.version() != version) throw stale();
    }
    private static void requireRequestSlaLive(BookingRow row) {
        if (row.slaDueAt() == null || !Instant.now().isBefore(row.slaDueAt())) {
            throw ApiException.conflict("BOOKING_REQUEST_EXPIRED",
                    "The vendor response window has expired");
        }
    }
    private static ApiException stale() { return ApiException.conflict("BOOKING_VERSION_CONFLICT", "Booking changed; refresh and try again"); }
    private static ApiException unavailable(Exception ex) { return ApiException.conflict("BOOKING_UNAVAILABLE", "The selected availability was just reserved by someone else"); }
    private static String requireIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > 100) throw ApiException.badRequest("IDEMPOTENCY_KEY_REQUIRED", "Provide an Idempotency-Key header (maximum 100 characters)");
        return key.trim();
    }
    private String hashRequest(BookingConfigurationRequest request) {
        try { return Sha256.hex(json.writeValueAsString(request)); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Could not hash booking request", ex); }
    }
    private String json(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Could not encode booking metadata", ex); }
    }
    private Map<String, Object> parseMap(String value) {
        try { return json.readValue(value, MAP_TYPE); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Invalid stored booking metadata", ex); }
    }
    private void requireBookingsEnabled() {
        if (!appConfig.getBoolean("feature.bookings", false)) {
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "BOOKINGS_DISABLED", "Bookings are temporarily unavailable");
        }
    }
    private static String sqlLiteral(String value) { return "'" + nullToEmpty(value).replace("'", "''") + "'"; }
    private static String holdCacheKey(UUID vendor, LocalDate date, AvailabilityMode mode, int slot, Instant start, Instant end) {
        return "booking:hold:" + vendor + ":" + date + ":" + mode + ":" + slot + ":" + start + ":" + end;
    }

    private record EventTime(Instant startAt, Instant endAt, LocalDate date, String timezone) {}
    private record CustomerBookingCursor(Instant createdAt, UUID id) {}
    private record VendorBookingCursor(int priority, Instant deadline, Instant createdAt, UUID id) {}
    private record RescheduleRow(UUID id, LocalDate eventDate, LocalTime startTime, LocalTime endTime,
            String eventTimezone, boolean customerApproved, boolean vendorApproved, String status, Instant createdAt) {}
    private record BookingRow(UUID id, String code, String idempotencyRequestHash, UUID customerId,
            UUID vendorId, String vendorSlug, String vendorName, UUID packageId, String packageTitle,
            String bookingMode, LocalDate eventDate, LocalTime startTime, LocalTime endTime,
            String eventTimezone, Instant eventStartAt, Instant eventEndAt, AvailabilityMode availabilityMode,
            int capacitySlot, Integer guests, VenueLocationMode venueLocationMode, String venueAddress,
            String venueCity, String venueCountry, Double venueLat, Double venueLng, String notes, String currency, long subtotalCents,
            long addOnsCents, long travelFeeCents,
            long discountCents, long totalCents, long paidCents, int depositPct, String cancellationPolicy,
            BookingStatus status, Instant holdExpiresAt, Instant slaDueAt, Instant paymentDueAt,
            Instant confirmedAt, Instant completedAt, Instant disputeWindowEndsAt, String cancelReason,
            boolean vendorPenaltyFlag, boolean allowSameDay, int rescheduleCount,
            long version, Instant createdAt) {}
}
