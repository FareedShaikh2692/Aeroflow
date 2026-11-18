package com.aeroflow.booking.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aeroflow.booking.domain.Booking;
import com.aeroflow.booking.domain.BookingStatus;
import com.aeroflow.booking.repo.BookingRepository;
import com.aeroflow.booking.repo.FlightInventoryRepository;
import com.aeroflow.common.events.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FlightDisruptionHandlerTest {
    @Mock EventInbox inbox;
    @Mock BookingRepository bookings;
    @Mock FlightInventoryRepository inventory;
    @Mock BookingService bookingService;
    @Mock FlightClient flightClient;
    @Mock DomainEventPublisher events;
    @InjectMocks FlightDisruptionHandler handler;
    final ObjectMapper mapper = new ObjectMapper();
    final UUID flightId = UUID.randomUUID();

    EventEnvelope env(String type, String status) {
        var p = mapper.createObjectNode().put("flightId", flightId.toString()).put("flightNumber", "AF100").put("status", status)
                .put("reason", "Weather").put("delayMinutes", 45).put("capacity", 300)
                .put("departureTime", "2030-01-01T08:00:00Z").put("arrivalTime", "2030-01-01T15:00:00Z");
        return new EventEnvelope(UUID.randomUUID(), type, "flight-service", flightId.toString(), Instant.now(), p);
    }

    Booking booking() {
        return new Booking("ABC234", new Booking.FlightSnapshot(flightId, "AF100", "DXB", "LHR", Instant.now(), Instant.now()),
                null, "a@b.co", null, "AED", "WEB", "guest");
    }

    @Test
    void cancellationCancelsAndRefundsEveryConfirmedBooking() {
        Booking b1 = booking(), b2 = booking();
        when(inbox.firstDelivery(any())).thenReturn(true);
        when(bookings.findByFlightIdAndStatus(flightId, BookingStatus.CONFIRMED)).thenReturn(List.of(b1, b2));
        handler.handle(env(EventTypes.FLIGHT_STATUS_CHANGED, "CANCELLED"));
        verify(bookingService).doCancel(eq(b1), contains("Weather"), eq(true));
        verify(bookingService).doCancel(eq(b2), anyString(), eq(true));
        verify(flightClient).evict(flightId);
    }

    @Test
    void delayEmitsPassengerNoticePerBooking() {
        when(inbox.firstDelivery(any())).thenReturn(true);
        when(bookings.findByFlightIdAndStatus(flightId, BookingStatus.CONFIRMED)).thenReturn(List.of(booking()));
        handler.handle(env(EventTypes.FLIGHT_STATUS_CHANGED, "DELAYED"));
        verify(events).publish(eq(Topics.BOOKING), eq(EventTypes.BOOKING_FLIGHT_NOTICE), anyString(),
                argThat((Map<String, Object> m) -> "DELAYED".equals(m.get("flightStatus")) && Integer.valueOf(45).equals(m.get("delayMinutes"))));
        verifyNoInteractions(bookingService);
    }

    @Test
    void duplicateDeliveryIsIgnored() {
        when(inbox.firstDelivery(any())).thenReturn(false);
        handler.handle(env(EventTypes.FLIGHT_STATUS_CHANGED, "CANCELLED"));
        verifyNoInteractions(bookings, bookingService, events);
    }

    @Test
    void unrelatedEventTypesAreSkippedWithoutTouchingTheInbox() {
        handler.handle(env(EventTypes.FLIGHT_CREATED, "SCHEDULED"));
        verifyNoInteractions(inbox, bookings);
    }
}
