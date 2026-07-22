package com.wedjan.api.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;

class CalendarSyncServiceTest {

    @Test
    void parsesAllDayTimedFoldedAndCancelledEvents() {
        String ics = """
                BEGIN:VCALENDAR\r
                VERSION:2.0\r
                BEGIN:VEVENT\r
                UID:three-days\r
                DTSTART;VALUE=DATE:20260801\r
                DTEND;VALUE=DATE:20260804\r
                SUMMARY:A long cele\r
                 bration\r
                END:VEVENT\r
                BEGIN:VEVENT\r
                UID:timed\r
                DTSTART;TZID=Australia/Melbourne:20261004T013000\r
                DTEND;TZID=Australia/Melbourne:20261004T033000\r
                END:VEVENT\r
                BEGIN:VEVENT\r
                UID:cancelled\r
                DTSTART;VALUE=DATE:20260810\r
                STATUS:CANCELLED\r
                END:VEVENT\r
                END:VCALENDAR\r
                """;
        var days = CalendarSyncService.parseBusyDays(ics, ZoneId.of("UTC"));
        assertThat(days).extracting(CalendarSyncService.BusyDay::date).containsExactly(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2),
                LocalDate.of(2026, 8, 3), LocalDate.of(2026, 10, 3));
        assertThat(days).noneMatch(day -> day.date().equals(LocalDate.of(2026, 8, 10)));
    }

    @Test
    void expandsRruleAndRdateWhileRemovingExdate() {
        ZoneId vendorZone = ZoneId.of("Asia/Kathmandu");
        LocalDate first = LocalDate.now(vendorZone).plusDays(30);
        LocalDate excluded = first.plusDays(1);
        LocalDate additional = first.plusDays(10);
        String ics = """
                BEGIN:VCALENDAR\r
                VERSION:2.0\r
                BEGIN:VEVENT\r
                UID:recurring-date\r
                DTSTART;VALUE=DATE:%s\r
                DTEND;VALUE=DATE:%s\r
                RRULE:FREQ=DAILY;COUNT=4\r
                RDATE;VALUE=DATE:%s\r
                EXDATE;VALUE=DATE:%s\r
                END:VEVENT\r
                END:VCALENDAR\r
                """.formatted(icsDate(first), icsDate(first.plusDays(1)),
                icsDate(additional), icsDate(excluded));

        var dates = CalendarSyncService.parseBusyDays(ics, vendorZone).stream()
                .map(CalendarSyncService.BusyDay::date)
                .toList();

        assertThat(dates).containsExactlyInAnyOrder(
                first, first.plusDays(2), first.plusDays(3), additional);
    }

    @Test
    void convertsUtcEventIntoVendorTimezoneAndBlocksBothSidesOfMidnight() {
        ZoneId vendorZone = ZoneId.of("Australia/Melbourne");
        LocalDate afterMidnight = LocalDate.now(vendorZone).plusDays(45);
        ZonedDateTime localStart = afterMidnight.minusDays(1).atTime(23, 30).atZone(vendorZone);
        ZonedDateTime localEnd = afterMidnight.atTime(0, 30).atZone(vendorZone);
        String ics = """
                BEGIN:VCALENDAR\r
                VERSION:2.0\r
                BEGIN:VEVENT\r
                UID:utc-midnight-boundary\r
                DTSTART:%s\r
                DTEND:%s\r
                END:VEVENT\r
                END:VCALENDAR\r
                """.formatted(icsInstant(localStart.toInstant()), icsInstant(localEnd.toInstant()));

        var dates = CalendarSyncService.parseBusyDays(ics, vendorZone).stream()
                .map(CalendarSyncService.BusyDay::date)
                .toList();

        assertThat(dates).containsExactly(
                afterMidnight.minusDays(1), afterMidnight);
    }

    private static String icsDate(LocalDate date) {
        return date.format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    private static String icsInstant(Instant instant) {
        return DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                .withZone(ZoneId.of("UTC"))
                .format(instant);
    }
}
