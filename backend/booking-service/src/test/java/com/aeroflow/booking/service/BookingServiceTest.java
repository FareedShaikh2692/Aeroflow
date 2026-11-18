package com.aeroflow.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aeroflow.booking.api.BookingDtos.*;
import com.aeroflow.booking.domain.*;
import com.aeroflow.booking.repo.*;
import com.aeroflow.common.audit.AuditService;
import com.aeroflow.common.error.ApiException;
import com.aeroflow.common.events.DomainEventPublisher;
import com.aeroflow.common.events.EventTypes;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingServiceTest {
    @Mock BookingRepository bookings;
    @Mock FlightInventoryRepository inventory;
    @Mock SeatAssignmentRepository seats;
    @Mock PaymentRepository payments;
    @Mock IdempotencyKeyRepository idempotency;
    @Mock FlightClient flights;
    @Mock PaymentGateway psp;
    @Mock DomainEventPublisher events;
    @Mock AuditService audit;

    static final Instant NOW = Instant.parse("2030-05-01T08:00:00Z");
    final UUID flightId = UUID.randomUUID();
    BookingService service;
    FlightInventory inv;

    @BeforeEach
    void setUp() {
        service = new BookingService(bookings, inventory, seats, payments, idempotency, flights, psp, new PnrGenerator(),
                events, audit, new ObjectMapper().registerModule(new JavaTimeModule()), Clock.fixed(NOW, ZoneOffset.UTC), 45);
        when(flights.get(flightId)).thenReturn(flight("SCHEDULED", NOW.plus(Duration.ofDays(1))));
        inv = new FlightInventory(flightId, "AF100", 26, "SCHEDULED");
        when(inventory.lockById(flightId)).thenReturn(Optional.of(inv));
        when(seats.occupiedSeats(flightId)).thenReturn(List.of("3A"));
        when(idempotency.findById(any())).thenReturn(Optional.empty());
        when(psp.charge(any(), any(), any(), any())).thenReturn(new PaymentGateway.ChargeResult(true, "MOCK-CH-1", "4242", "VISA", null));
        when(bookings.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    FlightClient.Flight flight(String status, Instant departure) {
        return new FlightClient.Flight(flightId, "AF100", new FlightClient.Airport("DXB", "Dubai"), new FlightClient.Airport("LHR", "London"),
                new FlightClient.Aircraft("A321neo", "AF-A321A", new FlightClient.Layout(1, "AC-DF", 3, "ABC-DEF")),
                departure, departure.plus(Duration.ofHours(7)), status, 0, "A1", "3",
                new BigDecimal("1000.00"), new BigDecimal("3200.00"), "AED", 22);
    }

    CreateBookingRequest request(String... seatNumbers) {
        List<PassengerRequest> pax = Arrays.stream(seatNumbers).map(s -> new PassengerRequest("Sarah", "Ahmed", null, "AE",
                "P1234567", null, s.startsWith("1") ? Cabin.BUSINESS : Cabin.ECONOMY, s)).toList();
        return new CreateBookingRequest(flightId, pax, "sarah@example.com", "+971500000000",
                new PaymentRequest("4242424242424242", "12/39", "123", "Sarah Ahmed"));
    }

    @Test
    void createsConfirmedBookingReservesSeatsChargesAndPublishesEvent() {
        BookingResponse res = service.create(request("1A", "2B"), "key-12345678");

        assertThat(res.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(res.pnr()).hasSize(6);
        assertThat(res.totalAmount()).isEqualByComparingTo("4200.00");
        assertThat(res.passengers()).extracting(PassengerResponse::seatNumber).containsExactly("1A", "2B");
        assertThat(res.passengers().get(0).passportMasked()).endsWith("567").doesNotContain("P1234");
        assertThat(inv.getBookedSeats()).isEqualTo(2);
        verify(seats, times(2)).save(any(SeatAssignment.class));
        verify(idempotency).save(any(IdempotencyKey.class));
        verify(events).publish(any(), eq(EventTypes.BOOKING_CONFIRMED), any(), any());
    }

    @Test
    void replaysIdempotentRequestWithoutChargingAgain() {
        BookingResponse first = service.create(request("2B"), "key-12345678");
        ArgumentCaptor<IdempotencyKey> key = ArgumentCaptor.forClass(IdempotencyKey.class);
        verify(idempotency).save(key.capture());
        ArgumentCaptor<Booking> saved = ArgumentCaptor.forClass(Booking.class);
        verify(bookings).save(saved.capture());
        when(idempotency.findById("key-12345678")).thenReturn(Optional.of(key.getValue()));
        when(bookings.findById(key.getValue().getBookingId())).thenReturn(Optional.of(saved.getValue()));

        BookingResponse replay = service.create(request("2B"), "key-12345678");

        assertThat(replay.pnr()).isEqualTo(first.pnr());
        verify(psp, times(1)).charge(any(), any(), any(), any());
    }

    @Test
    void rejectsIdempotencyKeyReusedWithDifferentPayload() {
        service.create(request("2B"), "key-12345678");
        ArgumentCaptor<IdempotencyKey> key = ArgumentCaptor.forClass(IdempotencyKey.class);
        verify(idempotency).save(key.capture());
        when(idempotency.findById("key-12345678")).thenReturn(Optional.of(key.getValue()));

        assertThatThrownBy(() -> service.create(request("2C"), "key-12345678"))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void requiresValidIdempotencyKey() {
        assertThatThrownBy(() -> service.create(request("2B"), null)).extracting("code").isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
        assertThatThrownBy(() -> service.create(request("2B"), "bad key!")).extracting("code").isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
    }

    @Test
    void declinedPaymentPersistsNothing() {
        when(psp.charge(any(), any(), any(), any())).thenReturn(new PaymentGateway.ChargeResult(false, null, "0002", "VISA", "Card declined"));
        assertThatThrownBy(() -> service.create(request("2B"), "key-12345678")).extracting("code").isEqualTo("PAYMENT_DECLINED");
        verify(bookings, never()).save(any());
        verify(seats, never()).save(any());
        assertThat(inv.getBookedSeats()).isZero();
    }

    @Test
    void refusesTakenSeatAndUnbookableFlights() {
        assertThatThrownBy(() -> service.create(request("3A"), "key-12345678")).extracting("code").isEqualTo("SEAT_UNAVAILABLE");

        when(flights.get(flightId)).thenReturn(flight("CANCELLED", NOW.plus(Duration.ofDays(1))));
        assertThatThrownBy(() -> service.create(request("2B"), "key-22345678")).extracting("code").isEqualTo("FLIGHT_NOT_BOOKABLE");

        when(flights.get(flightId)).thenReturn(flight("SCHEDULED", NOW.plus(Duration.ofMinutes(30))));
        assertThatThrownBy(() -> service.create(request("2B"), "key-32345678")).extracting("code").isEqualTo("BOOKING_CLOSED");
    }

    @Test
    void refusesWhenInventoryExhausted() {
        inv.reserve(26);
        assertThatThrownBy(() -> service.create(request("2B"), "key-12345678")).extracting("code").isEqualTo("INSUFFICIENT_SEATS");
    }

    @Test
    void guestCancellationRequiresLastNameReleasesSeatsAndRefunds() {
        service.create(request("2B"), "key-12345678");
        ArgumentCaptor<Booking> saved = ArgumentCaptor.forClass(Booking.class);
        verify(bookings).save(saved.capture());
        Booking b = saved.getValue();
        when(bookings.findByPnr(b.getPnr())).thenReturn(Optional.of(b));
        SeatAssignment sa = new SeatAssignment(flightId, "2B", b.getPassengers().get(0).getId());
        when(seats.findByPassengerIdAndReleasedFalse(any())).thenReturn(Optional.of(sa));
        when(psp.refund(any(), any(), any())).thenReturn("MOCK-RF-1");

        assertThatThrownBy(() -> service.cancel(b.getPnr(), new CancelRequest("Wrong", null))).extracting("code").isEqualTo("NOT_FOUND");
        BookingResponse res = service.cancel(b.getPnr(), new CancelRequest("ahmed", null));

        assertThat(res.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(sa.isReleased()).isTrue();
        assertThat(inv.getBookedSeats()).isZero();
        verify(payments).save(argThat(p -> "REFUND".equals(p.getType())));
        verify(events).publish(any(), eq(EventTypes.BOOKING_CANCELLED), any(), any());
        assertThatThrownBy(() -> service.cancel(b.getPnr(), new CancelRequest("Ahmed", null))).extracting("code").isEqualTo("ALREADY_CANCELLED");
    }

    @Test
    void checkedInBookingCannotBeSelfCancelled() {
        service.create(request("2B"), "key-12345678");
        ArgumentCaptor<Booking> saved = ArgumentCaptor.forClass(Booking.class);
        verify(bookings).save(saved.capture());
        Booking b = saved.getValue();
        b.getPassengers().get(0).checkIn();
        when(bookings.findByPnr(b.getPnr())).thenReturn(Optional.of(b));
        assertThatThrownBy(() -> service.cancel(b.getPnr(), new CancelRequest("Ahmed", null))).extracting("code").isEqualTo("CHECKED_IN");
    }

    @Test
    void retrieveHidesBookingWhenLastNameDoesNotMatch() {
        service.create(request("2B"), "key-12345678");
        ArgumentCaptor<Booking> saved = ArgumentCaptor.forClass(Booking.class);
        verify(bookings).save(saved.capture());
        when(bookings.findByPnr(saved.getValue().getPnr())).thenReturn(Optional.of(saved.getValue()));
        assertThat(service.retrieve(saved.getValue().getPnr().toLowerCase(), " AHMED ").pnr()).isEqualTo(saved.getValue().getPnr());
        assertThatThrownBy(() -> service.retrieve(saved.getValue().getPnr(), "Smith")).isInstanceOf(ApiException.class);
    }
}
