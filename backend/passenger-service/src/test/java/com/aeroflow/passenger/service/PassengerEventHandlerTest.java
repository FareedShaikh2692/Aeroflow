package com.aeroflow.passenger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aeroflow.common.events.EventEnvelope;
import com.aeroflow.common.events.EventInbox;
import com.aeroflow.common.events.EventTypes;
import com.aeroflow.passenger.domain.Passenger;
import com.aeroflow.passenger.domain.TravelRecord;
import com.aeroflow.passenger.repo.PassengerRepository;
import com.aeroflow.passenger.repo.TravelRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PassengerEventHandlerTest {
    final ObjectMapper mapper = new ObjectMapper();
    final EventInbox inbox = mock(EventInbox.class);
    final PassengerService passengers = mock(PassengerService.class);
    final PassengerRepository passengerRepo = mock(PassengerRepository.class);
    final TravelRecordRepository history = mock(TravelRecordRepository.class);
    final PassengerEventHandler handler = new PassengerEventHandler(inbox, passengers, passengerRepo, history);
    final UUID bookingId = UUID.randomUUID();

    @BeforeEach
    void setUp() { when(inbox.firstDelivery(any())).thenReturn(true); }

    EventEnvelope env(String type, ObjectNode p) {
        return new EventEnvelope(UUID.randomUUID(), type, "test", "x", Instant.now(), p);
    }

    @Test
    void registrationOfPassengerRoleCreatesProfileButStaffDoesNot() {
        UUID user = UUID.randomUUID();
        ObjectNode p = mapper.createObjectNode().put("userId", user.toString()).put("email", "s@x.co").put("firstName", "S").put("lastName", "A");
        p.putArray("roles").add("PASSENGER");
        handler.handle(env(EventTypes.USER_REGISTERED, p));
        verify(passengers).upsertFromRegistration(eq(user), eq("s@x.co"), eq("S"), eq("A"), any());
        ObjectNode staff = p.deepCopy();
        staff.putArray("roles").add("ADMIN");
        handler.handle(env(EventTypes.USER_REGISTERED, staff));
        verifyNoMoreInteractions(passengers);
    }

    @Test
    void confirmedBookingBuildsHistoryForContactAndTravellersAndAwardsPoints() {
        ObjectNode p = mapper.createObjectNode().put("bookingId", bookingId.toString()).put("pnr", "K7Q2MX").put("flightNumber", "AF100")
                .put("origin", "DXB").put("destination", "LHR").put("departureTime", "2030-01-01T08:00:00Z")
                .put("contactEmail", "Sarah@Example.com").put("totalAmount", 4000).put("currency", "AED");
        p.putArray("passengers").addObject().put("email", "adam@example.com");
        Passenger sarah = new Passenger(null, "Sarah", "Ahmed", "sarah@example.com", null);
        when(history.findByBookingIdAndEmailIgnoreCase(any(), any())).thenReturn(Optional.empty());
        when(passengerRepo.findByEmailIgnoreCase("sarah@example.com")).thenReturn(Optional.of(sarah));
        when(passengerRepo.findByEmailIgnoreCase("adam@example.com")).thenReturn(Optional.empty());

        handler.handle(env(EventTypes.BOOKING_CONFIRMED, p));

        ArgumentCaptor<TravelRecord> saved = ArgumentCaptor.forClass(TravelRecord.class);
        verify(history, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(TravelRecord::getEmail).containsExactly("sarah@example.com", "adam@example.com");
        assertThat(sarah.getLoyaltyPoints()).isEqualTo(2000);
    }

    @Test
    void cancellationAndCheckinUpdateHistory() {
        TravelRecord t = new TravelRecord("s@x.co", bookingId, "K7Q2MX", "AF100", "DXB", "LHR", Instant.now(), "CONFIRMED", null, "AED");
        when(history.findByBookingId(bookingId)).thenReturn(List.of(t));
        ObjectNode p = mapper.createObjectNode().put("bookingId", bookingId.toString());
        handler.handle(env(EventTypes.CHECKIN_COMPLETED, p));
        assertThat(t.isCheckedIn()).isTrue();
        handler.handle(env(EventTypes.BOOKING_CANCELLED, p));
        assertThat(t.getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void loyaltyTiersUpgradeAtThresholds() {
        Passenger px = new Passenger(null, "A", "B", "a@b.co", null);
        px.addPoints(15_000);
        assertThat(px.getLoyaltyTier()).isEqualTo("SILVER");
        px.addPoints(40_000);
        assertThat(px.getLoyaltyTier()).isEqualTo("GOLD");
    }
}
