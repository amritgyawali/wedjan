package com.wedjan.api.booking;

import com.wedjan.api.booking.BookingDtos.BookingActor;
import com.wedjan.api.booking.BookingDtos.BookingStatus;
import com.wedjan.api.common.ApiException;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class BookingStateMachine {

    private static final Map<BookingStatus, EnumSet<BookingStatus>> LEGAL = legalTransitions();

    public void require(BookingStatus from, BookingStatus to) {
        if (!LEGAL.getOrDefault(from, EnumSet.noneOf(BookingStatus.class)).contains(to)) {
            throw ApiException.conflict("BOOKING_INVALID_TRANSITION",
                    "Booking cannot move from " + from + " to " + to);
        }
    }

    public List<String> allowedActions(BookingStatus status, BookingActor viewer,
            Instant now, Instant holdExpiresAt, Instant slaDueAt,
            Instant eventStartAt, Instant eventEndAt, Instant disputeWindowEndsAt,
            boolean hasPendingReschedule, int rescheduleCount) {
        var actions = new java.util.ArrayList<String>();
        if (viewer == BookingActor.CUSTOMER) {
            if (status == BookingStatus.DRAFT && holdExpiresAt != null && now.isBefore(holdExpiresAt)) {
                actions.add("CONFIRM_PAYMENT_STUB");
            }
            if (status == BookingStatus.PENDING_PAYMENT && holdExpiresAt != null
                    && now.isBefore(holdExpiresAt)) actions.add("CONFIRM_PAYMENT_STUB");
            if (EnumSet.of(BookingStatus.DRAFT, BookingStatus.REQUESTED,
                    BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED).contains(status)) {
                actions.add("CANCEL");
            }
            if (status == BookingStatus.COMPLETED && disputeWindowEndsAt != null
                    && now.isBefore(disputeWindowEndsAt)) actions.add("DISPUTE");
        }
        if (viewer == BookingActor.VENDOR) {
            if (status == BookingStatus.REQUESTED && slaDueAt != null && now.isBefore(slaDueAt)) {
                actions.add("ACCEPT");
                actions.add("DECLINE");
            }
            if (EnumSet.of(BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED,
                    BookingStatus.IN_PROGRESS).contains(status)) actions.add("CANCEL");
            if ((status == BookingStatus.CONFIRMED || status == BookingStatus.IN_PROGRESS)
                    && eventEndAt != null && !now.isBefore(eventEndAt)) {
                actions.add("COMPLETE");
            }
        }
        if ((viewer == BookingActor.CUSTOMER || viewer == BookingActor.VENDOR)
                && status == BookingStatus.CONFIRMED) {
            if (hasPendingReschedule) {
                actions.add("RESPOND_RESCHEDULE");
            } else if (rescheduleCount == 0 && eventStartAt != null
                    && !eventStartAt.isBefore(now.plusSeconds(14L * 24 * 60 * 60))) {
                actions.add("PROPOSE_RESCHEDULE");
            }
        }
        return List.copyOf(actions);
    }

    public Map<BookingStatus, EnumSet<BookingStatus>> transitions() {
        var copy = new EnumMap<BookingStatus, EnumSet<BookingStatus>>(BookingStatus.class);
        LEGAL.forEach((key, value) -> copy.put(key, EnumSet.copyOf(value)));
        return Map.copyOf(copy);
    }

    private static Map<BookingStatus, EnumSet<BookingStatus>> legalTransitions() {
        var map = new EnumMap<BookingStatus, EnumSet<BookingStatus>>(BookingStatus.class);
        map.put(BookingStatus.DRAFT, EnumSet.of(BookingStatus.PENDING_PAYMENT,
                BookingStatus.CONFIRMED, BookingStatus.CANCELLED_BY_CUSTOMER,
                BookingStatus.EXPIRED));
        map.put(BookingStatus.REQUESTED, EnumSet.of(BookingStatus.VENDOR_ACCEPTED,
                BookingStatus.DECLINED, BookingStatus.CANCELLED_BY_CUSTOMER,
                BookingStatus.EXPIRED));
        map.put(BookingStatus.VENDOR_ACCEPTED, EnumSet.of(BookingStatus.PENDING_PAYMENT));
        map.put(BookingStatus.PENDING_PAYMENT, EnumSet.of(BookingStatus.CONFIRMED,
                BookingStatus.CANCELLED_BY_CUSTOMER, BookingStatus.CANCELLED_BY_VENDOR,
                BookingStatus.EXPIRED));
        map.put(BookingStatus.CONFIRMED, EnumSet.of(BookingStatus.IN_PROGRESS,
                BookingStatus.CANCELLED_BY_CUSTOMER, BookingStatus.CANCELLED_BY_VENDOR));
        map.put(BookingStatus.IN_PROGRESS, EnumSet.of(BookingStatus.COMPLETED,
                BookingStatus.CANCELLED_BY_VENDOR));
        map.put(BookingStatus.COMPLETED, EnumSet.of(BookingStatus.DISPUTED));
        return Map.copyOf(map);
    }
}
