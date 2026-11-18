package com.aeroflow.passenger.service;

import com.aeroflow.common.events.EventEnvelope;
import com.aeroflow.common.events.EventInbox;
import com.aeroflow.common.events.EventTypes;
import com.aeroflow.passenger.domain.TravelRecord;
import com.aeroflow.passenger.repo.PassengerRepository;
import com.aeroflow.passenger.repo.TravelRecordRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds the passenger travel-history read model from user/booking/check-in events. */
@Service
public class PassengerEventHandler {
    private final EventInbox inbox;
    private final PassengerService passengers;
    private final PassengerRepository passengerRepo;
    private final TravelRecordRepository history;

    public PassengerEventHandler(EventInbox inbox, PassengerService passengers, PassengerRepository passengerRepo,
                                 TravelRecordRepository history) {
        this.inbox = inbox;
        this.passengers = passengers;
        this.passengerRepo = passengerRepo;
        this.history = history;
    }

    @Transactional
    public void handle(EventEnvelope env) {
        if (!inbox.firstDelivery(env)) return;
        JsonNode p = env.payload();
        switch (env.type()) {
            case EventTypes.USER_REGISTERED -> {
                boolean passenger = false;
                for (JsonNode r : p.path("roles")) passenger |= "PASSENGER".equals(r.asText());
                if (passenger) passengers.upsertFromRegistration(UUID.fromString(p.path("userId").asText()),
                        p.path("email").asText(), p.path("firstName").asText(), p.path("lastName").asText(),
                        p.path("phone").asText(null));
            }
            case EventTypes.BOOKING_CONFIRMED -> onConfirmed(p);
            case EventTypes.BOOKING_CANCELLED -> history.findByBookingId(UUID.fromString(p.path("bookingId").asText()))
                    .forEach(t -> t.setStatus("CANCELLED"));
            case EventTypes.CHECKIN_COMPLETED -> history.findByBookingId(UUID.fromString(p.path("bookingId").asText()))
                    .forEach(TravelRecord::markCheckedIn);
            default -> { }
        }
    }

    private void onConfirmed(JsonNode p) {
        UUID bookingId = UUID.fromString(p.path("bookingId").asText());
        Set<String> emails = new LinkedHashSet<>();
        emails.add(p.path("contactEmail").asText().toLowerCase());
        for (JsonNode pax : p.path("passengers")) {
            String e = pax.path("email").asText("");
            if (!e.isBlank() && !"null".equals(e)) emails.add(e.toLowerCase());
        }
        for (String email : emails) {
            if (history.findByBookingIdAndEmailIgnoreCase(bookingId, email).isPresent()) continue;
            history.save(new TravelRecord(email, bookingId, p.path("pnr").asText(), p.path("flightNumber").asText(),
                    p.path("origin").asText(), p.path("destination").asText(),
                    Instant.parse(p.path("departureTime").asText()), "CONFIRMED",
                    p.path("totalAmount").decimalValue(), p.path("currency").asText()));
            passengerRepo.findByEmailIgnoreCase(email).ifPresent(px -> px.addPoints(p.path("totalAmount").asInt() / 2));
        }
    }
}
