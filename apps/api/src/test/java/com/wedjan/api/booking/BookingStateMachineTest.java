package com.wedjan.api.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wedjan.api.booking.BookingDtos.BookingStatus;
import com.wedjan.api.common.ApiException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BookingStateMachineTest {

    private static final Map<BookingStatus, EnumSet<BookingStatus>> EXPECTED_TRANSITIONS =
            expectedTransitionsFromPhaseFourSpec();

    private final BookingStateMachine machine = new BookingStateMachine();

    @Test
    void everySpecifiedTransitionIsLegalAndEveryOtherStatusPairIsRejected() {
        for (BookingStatus from : BookingStatus.values()) {
            for (BookingStatus to : BookingStatus.values()) {
                boolean legal = EXPECTED_TRANSITIONS.get(from).contains(to);
                if (legal) {
                    machine.require(from, to);
                } else {
                    assertThatThrownBy(() -> machine.require(from, to))
                            .isInstanceOfSatisfying(ApiException.class,
                                    ex -> assertThat(ex.code()).isEqualTo("BOOKING_INVALID_TRANSITION"));
                }
            }
        }
    }

    private static Map<BookingStatus, EnumSet<BookingStatus>> expectedTransitionsFromPhaseFourSpec() {
        var expected = new EnumMap<BookingStatus, EnumSet<BookingStatus>>(BookingStatus.class);
        for (BookingStatus status : BookingStatus.values()) {
            expected.put(status, EnumSet.noneOf(BookingStatus.class));
        }
        expected.put(BookingStatus.DRAFT, EnumSet.of(
                BookingStatus.PENDING_PAYMENT,
                BookingStatus.CONFIRMED,
                BookingStatus.CANCELLED_BY_CUSTOMER,
                BookingStatus.EXPIRED));
        expected.put(BookingStatus.REQUESTED, EnumSet.of(
                BookingStatus.VENDOR_ACCEPTED,
                BookingStatus.DECLINED,
                BookingStatus.CANCELLED_BY_CUSTOMER,
                BookingStatus.EXPIRED));
        expected.put(BookingStatus.VENDOR_ACCEPTED, EnumSet.of(
                BookingStatus.PENDING_PAYMENT));
        expected.put(BookingStatus.PENDING_PAYMENT, EnumSet.of(
                BookingStatus.CONFIRMED,
                BookingStatus.CANCELLED_BY_CUSTOMER,
                BookingStatus.CANCELLED_BY_VENDOR,
                BookingStatus.EXPIRED));
        expected.put(BookingStatus.CONFIRMED, EnumSet.of(
                BookingStatus.IN_PROGRESS,
                BookingStatus.CANCELLED_BY_CUSTOMER,
                BookingStatus.CANCELLED_BY_VENDOR));
        expected.put(BookingStatus.IN_PROGRESS, EnumSet.of(
                BookingStatus.COMPLETED,
                BookingStatus.CANCELLED_BY_VENDOR));
        expected.put(BookingStatus.COMPLETED, EnumSet.of(
                BookingStatus.DISPUTED));
        return Map.copyOf(expected);
    }
}
