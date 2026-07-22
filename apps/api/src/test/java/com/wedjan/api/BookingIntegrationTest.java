package com.wedjan.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wedjan.api.auth.MailService;
import com.wedjan.api.booking.AvailabilityService;
import com.wedjan.api.booking.BookingDtos.Booking;
import com.wedjan.api.booking.BookingDtos.BookingAcceptRequest;
import com.wedjan.api.booking.BookingDtos.BookingCancelRequest;
import com.wedjan.api.booking.BookingDtos.BookingDisputeRequest;
import com.wedjan.api.booking.BookingDtos.CalendarSyncStatus;
import com.wedjan.api.booking.BookingDtos.ExternalCalendarRequest;
import com.wedjan.api.booking.BookingDtos.BookingConfigurationRequest;
import com.wedjan.api.booking.BookingDtos.BookingStatus;
import com.wedjan.api.booking.BookingDtos.BookingVersionRequest;
import com.wedjan.api.booking.BookingDtos.AvailabilityMode;
import com.wedjan.api.booking.BookingDtos.AvailabilityRuleRequest;
import com.wedjan.api.booking.BookingDtos.AvailabilitySettingsRequest;
import com.wedjan.api.booking.BookingDtos.AvailabilitySlot;
import com.wedjan.api.booking.BookingDtos.AvailabilityExceptionRequest;
import com.wedjan.api.booking.BookingDtos.ExceptionType;
import com.wedjan.api.booking.BookingDtos.RescheduleDecisionRequest;
import com.wedjan.api.booking.BookingDtos.RescheduleProposalRequest;
import com.wedjan.api.booking.BookingDtos.VenueLocationMode;
import com.wedjan.api.booking.BookingService;
import com.wedjan.api.booking.CalendarSyncService;
import com.wedjan.api.common.ApiException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(properties = {
        "wedjan.booking.scheduler-initial-delay-ms=3600000",
        "wedjan.calendar.sync-initial-delay-ms=3600000",
        "wedjan.booking.payment-stub-enabled=true",
        "wedjan.calendar.allow-private-urls=true"
})
@AutoConfigureMockMvc
class BookingIntegrationTest {

    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
    @Container @SuppressWarnings("resource") static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired BookingService bookings;
    @Autowired AvailabilityService availability;
    @Autowired CalendarSyncService calendars;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockitoBean MailService mail;

    @Test
    void concurrencyStateLifecycleAvailabilityRescheduleAndIcsWorkEndToEnd() throws Exception {
        UUID vendor = account("phase4.vendor@example.com", "VENDOR");
        UUID customer = account("phase4.customer@example.com", "CUSTOMER");
        UUID category = jdbc.queryForObject("SELECT id FROM categories WHERE slug='wedding-photography'", UUID.class);
        vendor(vendor, category);
        UUID instantPackage = packageRow(vendor, category, "Instant coverage", "instant-coverage", "INSTANT");
        UUID requestPackage = packageRow(vendor, category, "Request coverage", "request-coverage", "REQUEST");
        LocalDate contested = LocalDate.now().plusDays(50);

        assertThat(bookings.quote(customer, config(instantPackage, contested)).price().travelFeeCents())
                .isEqualTo(5_000);
        assertThat(bookings.quote(customer, vendorConfig(instantPackage, contested.plusDays(12)))
                .price().travelFeeCents()).isZero();
        Booking atVendor = bookings.create(customer, "vendor-location",
                vendorConfig(instantPackage, contested.plusDays(12)));
        assertThat(atVendor.venueLocationMode()).isEqualTo(VenueLocationMode.VENDOR);
        assertThat(atVendor.venueCity()).isEqualTo("Kathmandu");
        assertThat(atVendor.venueCountry()).isEqualTo("NP");
        assertThat(atVendor.venueAddress()).isNull();
        assertThat(atVendor.price().travelFeeCents()).isZero();
        bookings.confirmPaymentStub(customer, atVendor.id(), new BookingVersionRequest(atVendor.version()));
        assertThatThrownBy(() -> bookings.quote(customer,
                regionConfig(instantPackage, contested, "Melbourne", "AU")))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.code()).isEqualTo("BOOKING_OUTSIDE_SERVICE_AREA"));
        assertThatThrownBy(() -> bookings.quote(customer,
                regionConfig(instantPackage, contested, null, null)))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.code()).isEqualTo("BOOKING_VENUE_LOCATION_REQUIRED"));

        LocalDate importedBusyDate = contested.plusDays(20);
        var server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] calendarBody = ("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\nUID:external-busy\r\n"
                + "DTSTART;VALUE=DATE:" + importedBusyDate.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
                + "\r\nDTEND;VALUE=DATE:" + importedBusyDate.plusDays(1).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
                + "\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n").getBytes(StandardCharsets.UTF_8);
        var servedCalendar = new java.util.concurrent.atomic.AtomicReference<>(calendarBody);
        server.createContext("/busy.ics", exchange -> {
            byte[] body = servedCalendar.get();
            exchange.getResponseHeaders().set("Content-Type", "text/calendar");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        var connected = calendars.connect(vendor, new ExternalCalendarRequest(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/busy.ics"));
        assertThat(connected.syncStatus()).isEqualTo(CalendarSyncStatus.HEALTHY);
        assertThat(availability.publicAvailability("phase-four-studio", importedBusyDate, importedBusyDate)
                .days().getFirst().status().name()).isEqualTo("BLACKED_OUT");
        servedCalendar.set("<html>temporary upstream error</html>".getBytes(StandardCharsets.UTF_8));
        jdbc.update("UPDATE external_calendars SET last_synced_at=now()-interval '2 minutes' WHERE id=?",
                connected.id());
        var degraded = calendars.syncNow(vendor, connected.id());
        assertThat(degraded.syncStatus()).isEqualTo(CalendarSyncStatus.DEGRADED);
        assertThat(availability.publicAvailability("phase-four-studio", importedBusyDate, importedBusyDate)
                .days().getFirst().status().name()).isEqualTo("BLACKED_OUT");
        server.stop(0);

        var start = new CountDownLatch(1);
        var outcomes = java.util.Collections.synchronizedList(new ArrayList<Object>());
        try (var executor = Executors.newFixedThreadPool(20)) {
            for (int i = 0; i < 20; i++) {
                int attempt = i;
                executor.submit(() -> {
                    try {
                        start.await();
                        outcomes.add(bookings.create(customer, "parallel-" + attempt,
                                config(instantPackage, contested)));
                    } catch (Exception ex) {
                        outcomes.add(ex);
                    }
                });
            }
            start.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
        List<Booking> winners = outcomes.stream().filter(Booking.class::isInstance)
                .map(Booking.class::cast).toList();
        assertThat(winners).withFailMessage(() -> "Parallel outcomes: " + outcomes.stream()
                .map(value -> value instanceof Throwable error
                        ? error.getClass().getSimpleName() + ":" + error.getMessage() + " CAUSE="
                            + (error.getCause() == null ? "none" : error.getCause().getMessage())
                        : value.toString())
                .toList()).hasSize(1);
        assertThat(outcomes).hasSize(20);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM booking_holds WHERE event_date=? AND released_at IS NULL",
                Long.class, contested)).isEqualTo(1);
        Booking held = winners.getFirst();
        assertThat(held.venueCity()).isEqualTo("Kathmandu");
        assertThat(held.venueCountry()).isEqualTo("NP");
        String winningKey = jdbc.queryForObject("SELECT idempotency_key FROM bookings WHERE id=?",
                String.class, held.id());
        assertThat(bookings.create(customer, winningKey, config(instantPackage, contested)).id())
                .isEqualTo(held.id());
        assertThatThrownBy(() -> bookings.create(customer, winningKey,
                config(instantPackage, contested.plusDays(2))))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.code()).isEqualTo("BOOKING_IDEMPOTENCY_MISMATCH"));
        assertThat(availability.publicAvailability("phase-four-studio", contested, contested)
                .days().getFirst().status().name()).isEqualTo("BOOKED");
        mvc.perform(get("/api/v1/search/vendors").param("category", "wedding-photography")
                        .param("date", contested.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));

        Booking confirmedInstant = bookings.confirmPaymentStub(customer, held.id(),
                new BookingVersionRequest(held.version()));
        assertThat(confirmedInstant.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(confirmedInstant.events()).extracting(event -> event.toStatus().name())
                .containsExactly("DRAFT", "CONFIRMED");

        LocalDate requestDate = contested.plusDays(1);
        Booking requested = bookings.create(customer, "request-cycle", config(requestPackage, requestDate));
        assertThat(requested.status()).isEqualTo(BookingStatus.REQUESTED);
        Booking pendingPayment = bookings.accept(vendor, requested.id(),
                new BookingAcceptRequest(12_345L, null, requested.version()));
        assertThat(pendingPayment.status()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(pendingPayment.events()).extracting(event -> event.toStatus().name())
                .containsExactly("REQUESTED", "VENDOR_ACCEPTED", "PENDING_PAYMENT");
        Booking confirmed = bookings.confirmPaymentStub(customer, requested.id(),
                new BookingVersionRequest(pendingPayment.version()));
        assertThat(confirmed.price().travelFeeCents()).isEqualTo(12_345);
        jdbc.update("UPDATE bookings SET event_start_at=now()-interval '3 days',event_end_at=now()-interval '2 days' WHERE id=?",
                confirmed.id());
        bookings.automateTransitions();
        Booking completed = bookings.get(customer, confirmed.id());
        assertThat(completed.status()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(completed.disputeWindowEndsAt()).isAfter(completed.serverNow());

        calendars.exportUrl(vendor, false);
        String feed = calendars.export(jdbc.queryForObject(
                "SELECT calendar_export_token FROM vendor_profiles WHERE account_id=?", UUID.class, vendor));
        assertThat(feed).contains("BEGIN:VCALENDAR", "UID:" + completed.code() + "@wedjan", "END:VCALENDAR");
        assertThat(CalendarSyncService.parseBusyDays(feed, ZoneId.of("UTC"))).isNotEmpty();
        Booking disputed = bookings.dispute(customer, completed.id(),
                new BookingDisputeRequest("The agreed delivery is incomplete", completed.version()));
        assertThat(disputed.status()).isEqualTo(BookingStatus.DISPUTED);

        LocalDate cancellationDate = contested.plusDays(5);
        Booking cancellable = bookings.create(customer, "cancel-preview", config(requestPackage, cancellationDate));
        Booking cancellablePending = bookings.accept(vendor, cancellable.id(),
                new BookingAcceptRequest(0L, null, cancellable.version()));
        Booking cancellableConfirmed = bookings.confirmPaymentStub(customer, cancellable.id(),
                new BookingVersionRequest(cancellablePending.version()));
        var preview = bookings.refundPreview(customer, cancellable.id());
        assertThat(preview.refundPercent()).isEqualTo(50);
        Booking cancelled = bookings.cancel(customer, cancellable.id(),
                new BookingCancelRequest("Plans changed", cancellableConfirmed.version(),
                        preview.calculationId()));
        assertThat(cancelled.status()).isEqualTo(BookingStatus.CANCELLED_BY_CUSTOMER);
        assertThat(jdbc.queryForObject("SELECT refundable_cents FROM refund_calculations WHERE booking_id=? AND status='FINAL'",
                Long.class, cancellable.id())).isEqualTo(preview.refundableCents());

        LocalDate originalDate = contested.plusDays(30);
        Booking forReschedule = bookings.create(customer, "reschedule", config(requestPackage, originalDate));
        Booking reschedulePending = bookings.accept(vendor, forReschedule.id(),
                new BookingAcceptRequest(0L, null, forReschedule.version()));
        Booking rescheduleConfirmed = bookings.confirmPaymentStub(customer, forReschedule.id(),
                new BookingVersionRequest(reschedulePending.version()));
        LocalDate newDate = originalDate.plusDays(2);
        Booking proposed = bookings.proposeReschedule(customer, forReschedule.id(),
                new RescheduleProposalRequest(newDate, null, null, "Asia/Kathmandu", rescheduleConfirmed.version()));
        assertThat(proposed.pendingReschedule()).isNotNull();
        Booking moved = bookings.decideReschedule(vendor, forReschedule.id(),
                new RescheduleDecisionRequest(true, proposed.version()));
        assertThat(moved.eventDate()).isEqualTo(newDate);
        assertThat(moved.pendingReschedule()).isNull();
        assertThatThrownBy(() -> bookings.proposeReschedule(customer, forReschedule.id(),
                new RescheduleProposalRequest(newDate.plusDays(1), null, null,
                        "Asia/Kathmandu", moved.version())))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.code()).isEqualTo("BOOKING_RESCHEDULE_NOT_ALLOWED"));

        mvc.perform(get("/api/v1/search/vendors").param("category", "wedding-photography")
                        .param("date", contested.plusDays(10).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].nextAvailableDates.length()").value(3));

        jdbc.update("UPDATE availability_rules SET jobs_per_day=2 WHERE vendor_id=? AND deleted_at IS NULL",
                vendor);
        UUID capacityCustomerOne = account("capacity.one@example.com", "CUSTOMER");
        UUID capacityCustomerTwo = account("capacity.two@example.com", "CUSTOMER");
        UUID capacityCustomerThree = account("capacity.three@example.com", "CUSTOMER");
        LocalDate capacityDate = contested.plusDays(40);
        Booking firstCapacity = bookings.create(capacityCustomerOne, "capacity-one",
                config(instantPackage, capacityDate));
        bookings.checkout(capacityCustomerOne, firstCapacity.id(),
                new BookingVersionRequest(firstCapacity.version()));
        assertThat(availability.publicAvailability("phase-four-studio", capacityDate, capacityDate)
                .days().getFirst().status().name()).isEqualTo("LIMITED");
        Booking secondCapacity = bookings.create(capacityCustomerTwo, "capacity-two",
                config(instantPackage, capacityDate));
        assertThat(secondCapacity.id()).isNotNull();
        assertThat(availability.publicAvailability("phase-four-studio", capacityDate, capacityDate)
                .days().getFirst().status().name()).isEqualTo("BOOKED");
        assertThatThrownBy(() -> bookings.create(capacityCustomerThree, "capacity-three",
                config(instantPackage, capacityDate)))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.code()).isEqualTo("BOOKING_DATE_UNAVAILABLE"));

        UUID slotVendor = account("slot.vendor@example.com", "VENDOR");
        vendor(slotVendor, category, "Slot Studio", "slot-studio");
        var definedSlot = new AvailabilitySlot(LocalTime.of(10, 0), LocalTime.of(11, 0), "Morning");
        List<AvailabilityRuleRequest> slotRules = java.util.stream.IntStream.range(0, 7)
                .mapToObj(day -> new AvailabilityRuleRequest(day, true, List.of(definedSlot), 1)).toList();
        availability.updateSettings(slotVendor, new AvailabilitySettingsRequest(
                AvailabilityMode.SLOT, "Asia/Kathmandu", slotRules));
        UUID slotPackage = packageRow(slotVendor, category, "Morning studio", "morning-studio", "INSTANT");
        UUID slotCustomer = account("slot.customer@example.com", "CUSTOMER");
        LocalDate slotDate = contested.plusDays(60);
        assertThatThrownBy(() -> bookings.create(slotCustomer, "slot-timezone-mismatch",
                slotConfig(slotPackage, slotDate, LocalTime.of(10, 0), LocalTime.of(11, 0), "UTC")))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.code()).isEqualTo("BOOKING_TIMEZONE_MISMATCH"));
        assertThatThrownBy(() -> bookings.create(slotCustomer, "slot-subrange",
                slotConfig(slotPackage, slotDate, LocalTime.of(10, 15),
                        LocalTime.of(10, 45), "Asia/Kathmandu")))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.code()).isEqualTo("BOOKING_SLOT_UNAVAILABLE"));
        Booking exactSlot = bookings.create(slotCustomer, "slot-exact",
                slotConfig(slotPackage, slotDate, LocalTime.of(10, 0),
                        LocalTime.of(11, 0), "Asia/Kathmandu"));
        assertThat(exactSlot.id()).isNotNull();
        assertThat(availability.publicAvailability("slot-studio", slotDate, slotDate)
                .days().getFirst().slots().getFirst().status().name()).isEqualTo("BOOKED");
        LocalDate slotBlackout = slotDate.plusDays(1);
        availability.addException(slotVendor, new AvailabilityExceptionRequest(
                slotBlackout, ExceptionType.BLACKOUT, List.of(), "Closed"));
        var blackedOutSlotDay = availability.publicAvailability(
                "slot-studio", slotBlackout, slotBlackout).days().getFirst();
        assertThat(blackedOutSlotDay.status().name()).isEqualTo("BLACKED_OUT");
        assertThat(blackedOutSlotDay.slots()).allMatch(slot -> slot.status().name().equals("BLACKED_OUT"));
        List<AvailabilityRuleRequest> dateRules = java.util.stream.IntStream.range(0, 7)
                .mapToObj(day -> new AvailabilityRuleRequest(day, true, List.of(), 1)).toList();
        assertThatThrownBy(() -> availability.updateSettings(slotVendor,
                new AvailabilitySettingsRequest(AvailabilityMode.DATE, "Asia/Kathmandu", dateRules)))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.code()).isEqualTo("AVAILABILITY_MODE_ACTIVE_BOOKINGS"));
    }

    private UUID account(String email, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO accounts(id,email,password_hash,status) VALUES (?,?,?,'ACTIVE')", id, email, "test");
        jdbc.update("INSERT INTO account_roles(id,account_id,role) VALUES (?,?,?)", UUID.randomUUID(), id, role);
        return id;
    }

    private void vendor(UUID id, UUID category) {
        vendor(id, category, "Phase Four Studio", "phase-four-studio");
    }

    private void vendor(UUID id, UUID category, String name, String slug) {
        jdbc.update("""
                INSERT INTO vendor_profiles(account_id,business_name,slug,tagline,about,base_city,base_country,
                  lat,lng,status,is_public,onboarding_step,currency,availability_mode,timezone)
                VALUES (?,?,?,'Always ready','A trusted wedding studio with priced packages.',
                  'Kathmandu','NP',27.7172,85.324,'VERIFIED',true,7,'NPR','DATE','Asia/Kathmandu')
                """, id, name, slug);
        jdbc.update("INSERT INTO vendor_categories(id,vendor_id,category_id,is_primary) VALUES (?,?,?,true)",
                UUID.randomUUID(), id, category);
        jdbc.update("INSERT INTO service_areas(id,vendor_id,mode,city,country,travel_fee_cents) VALUES (?,?,'REGION','Kathmandu','NP',5000)",
                UUID.randomUUID(), id);
    }

    private UUID packageRow(UUID vendor, UUID category, String title, String slug, String mode) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO packages(id,vendor_id,category_id,title,slug,price_cents,currency,pricing_model,
                  duration_minutes,booking_mode,deposit_pct,cancellation_policy,status)
                VALUES (?,?,?,?,?,250000,'NPR','FLAT',480,?,25,'MODERATE','PUBLISHED')
                """, id, vendor, category, title, slug, mode);
        return id;
    }

    private BookingConfigurationRequest config(UUID packageId, LocalDate date) {
        return new BookingConfigurationRequest(packageId, date, null, null, "Asia/Kathmandu", 100,
                VenueLocationMode.TRAVEL, "Kathmandu, Nepal", "Kathmandu", "np", null, null,
                "Wedding celebration", List.of(), 0L);
    }

    private BookingConfigurationRequest slotConfig(UUID packageId, LocalDate date,
            LocalTime start, LocalTime end, String timezone) {
        return new BookingConfigurationRequest(packageId, date, start, end, timezone, 20,
                VenueLocationMode.TRAVEL, "Kathmandu, Nepal", "Kathmandu", "NP", null, null,
                "Studio booking", List.of(), 0L);
    }

    private BookingConfigurationRequest regionConfig(UUID packageId, LocalDate date,
            String city, String country) {
        return new BookingConfigurationRequest(packageId, date, null, null, "Asia/Kathmandu", 100,
                VenueLocationMode.TRAVEL, "Test venue", city, country, null, null,
                "Region check", List.of(), 0L);
    }

    private BookingConfigurationRequest vendorConfig(UUID packageId, LocalDate date) {
        return new BookingConfigurationRequest(packageId, date, null, null, "Asia/Kathmandu", 100,
                VenueLocationMode.VENDOR, null, null, null, null, null,
                "At the vendor location", List.of(), 0L);
    }
}
