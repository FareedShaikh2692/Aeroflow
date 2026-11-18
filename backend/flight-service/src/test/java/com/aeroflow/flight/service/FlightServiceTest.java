package com.aeroflow.flight.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aeroflow.common.audit.AuditService;
import com.aeroflow.common.events.DomainEventPublisher;
import com.aeroflow.common.events.EventTypes;
import com.aeroflow.flight.api.FlightDtos.*;
import com.aeroflow.flight.domain.*;
import com.aeroflow.flight.repo.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FlightServiceTest {
    @Mock FlightRepository flights;
    @Mock AirportRepository airports;
    @Mock AircraftRepository aircraft;
    @Mock DomainEventPublisher events;
    @Mock AuditService audit;
    @Mock BookingClient bookings;
    static final Instant NOW = Instant.parse("2030-03-01T00:00:00Z");
    FlightService service;
    final Airport dxb = new Airport("DXB", "Dubai", "Dubai", "AE", "Asia/Dubai");
    final Airport lhr = new Airport("LHR", "Heathrow", "London", "UK", "Europe/London");
    final Aircraft ac = new Aircraft("AF-1", "A350", "Airbus", 2, "AC-DF", 10, "ABC-DEF");

    @BeforeEach
    void setUp() {
        service = new FlightService(flights, airports, aircraft, events, audit, bookings, Clock.fixed(NOW, ZoneOffset.UTC));
        when(airports.findById("DXB")).thenReturn(Optional.of(dxb));
        when(airports.findById("LHR")).thenReturn(Optional.of(lhr));
        when(aircraft.findById(ac.getId())).thenReturn(Optional.of(ac));
    }

    FlightRequest req(String from, String to, Instant dep, Instant arr, String eco, String bus) {
        return new FlightRequest("AF900", from, to, ac.getId(), dep, arr, new BigDecimal(eco), new BigDecimal(bus), "B1", "3");
    }

    Flight flight() {
        Instant dep = NOW.plus(Duration.ofDays(2));
        return new Flight("AF900", dxb, lhr, ac, dep, dep.plus(Duration.ofHours(7)), BigDecimal.TEN, BigDecimal.TEN, "AED");
    }

    @Test
    void createValidatesRouteTimesAndFares() {
        Instant dep = NOW.plus(Duration.ofDays(3));
        assertThatThrownBy(() -> service.create(req("DXB", "DXB", dep, dep.plusSeconds(3600), "1", "2"))).extracting("code").isEqualTo("INVALID_ROUTE");
        assertThatThrownBy(() -> service.create(req("DXB", "LHR", dep, dep.minusSeconds(60), "1", "2"))).extracting("code").isEqualTo("INVALID_TIMES");
        assertThatThrownBy(() -> service.create(req("DXB", "LHR", dep, dep.plus(Duration.ofHours(21)), "1", "2"))).extracting("code").isEqualTo("INVALID_TIMES");
        assertThatThrownBy(() -> service.create(req("DXB", "LHR", dep, dep.plusSeconds(3600), "5", "2"))).extracting("code").isEqualTo("INVALID_FARE");
        verify(flights, never()).save(any());
    }

    @Test
    void createPersistsPublishesAndAudits() {
        Instant dep = NOW.plus(Duration.ofDays(3));
        FlightResponse r = service.create(req("DXB", "LHR", dep, dep.plus(Duration.ofHours(7)), "1000", "3000"));
        assertThat(r.flightNumber()).isEqualTo("AF900");
        assertThat(r.capacity()).isEqualTo(68);
        verify(events).publish(any(), eq(EventTypes.FLIGHT_CREATED), any(), any());
        verify(audit).record(eq("FLIGHT_CREATED"), eq("Flight"), any(), any());
    }

    @Test
    void duplicateFlightIsRejected() {
        when(flights.existsByFlightNumberAndDepartureTime(any(), any())).thenReturn(true);
        Instant dep = NOW.plus(Duration.ofDays(3));
        assertThatThrownBy(() -> service.create(req("DXB", "LHR", dep, dep.plusSeconds(3600), "1", "2"))).extracting("code").isEqualTo("DUPLICATE_FLIGHT");
    }

    @Test
    void delayRequiresMinutesAndCancellationRequiresReason() {
        Flight f = flight();
        when(flights.findById(f.getId())).thenReturn(Optional.of(f));
        assertThatThrownBy(() -> service.changeStatus(f.getId(), new StatusChangeRequest(FlightStatus.DELAYED, null, null, null)))
                .extracting("code").isEqualTo("DELAY_REQUIRED");
        assertThatThrownBy(() -> service.changeStatus(f.getId(), new StatusChangeRequest(FlightStatus.CANCELLED, null, " ", null)))
                .extracting("code").isEqualTo("REASON_REQUIRED");
        assertThatThrownBy(() -> service.changeStatus(f.getId(), new StatusChangeRequest(FlightStatus.ARRIVED, null, null, null)))
                .extracting("code").isEqualTo("INVALID_STATUS_TRANSITION");
    }

    @Test
    void statusChangePublishesEventWithPreviousStatus() {
        Flight f = flight();
        when(flights.findById(f.getId())).thenReturn(Optional.of(f));
        FlightResponse r = service.changeStatus(f.getId(), new StatusChangeRequest(FlightStatus.DELAYED, 30, "Late inbound", "C7"));
        assertThat(r.status()).isEqualTo(FlightStatus.DELAYED);
        assertThat(r.gate()).isEqualTo("C7");
        verify(events).publish(any(), eq(EventTypes.FLIGHT_STATUS_CHANGED), any(),
                argThat((Map<String, Object> m) -> "SCHEDULED".equals(m.get("previousStatus")) && "DELAYED".equals(m.get("status"))));
    }

    @Test
    void deleteIsBlockedWhileBookingsExist() {
        Flight f = flight();
        when(flights.findById(f.getId())).thenReturn(Optional.of(f));
        when(bookings.activeBookingCount(f.getId())).thenReturn(4L);
        assertThatThrownBy(() -> service.delete(f.getId())).extracting("code").isEqualTo("FLIGHT_HAS_BOOKINGS");
        verify(flights, never()).delete(any(com.aeroflow.flight.domain.Flight.class));
        when(bookings.activeBookingCount(f.getId())).thenReturn(0L);
        service.delete(f.getId());
        verify(flights).delete(f);
        verify(events).publish(any(), eq(EventTypes.FLIGHT_DELETED), any(), any());
    }

    @Test
    void updateRejectsStaleVersionAndLockedFlights() {
        Flight f = flight();
        when(flights.findById(f.getId())).thenReturn(Optional.of(f));
        Instant dep = NOW.plus(Duration.ofDays(3));
        assertThatThrownBy(() -> service.update(f.getId(), req("DXB", "LHR", dep, dep.plusSeconds(7200), "1", "2"), 7L))
                .extracting("code").isEqualTo("STALE_VERSION");
        f.changeStatus(FlightStatus.CANCELLED, null, "x");
        assertThatThrownBy(() -> service.update(f.getId(), req("DXB", "LHR", dep, dep.plusSeconds(7200), "1", "2"), null))
                .extracting("code").isEqualTo("FLIGHT_LOCKED");
    }

    @Test
    void searchUsesOriginLocalDayAndHidesNearDepartures() {
        Flight later = flight();
        when(flights.search(eq("DXB"), eq("LHR"), any(), any())).thenReturn(List.of(later));
        List<FlightResponse> res = service.search("dxb", "lhr", LocalDate.of(2030, 3, 3));
        assertThat(res).hasSize(1);
        verify(flights).search("DXB", "LHR", Instant.parse("2030-03-02T20:00:00Z"), Instant.parse("2030-03-03T20:00:00Z"));
    }
}
