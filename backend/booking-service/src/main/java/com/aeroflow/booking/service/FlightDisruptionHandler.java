package com.aeroflow.booking.service;

import com.aeroflow.booking.domain.Booking;
import com.aeroflow.booking.domain.BookingStatus;
import com.aeroflow.booking.repo.BookingRepository;
import com.aeroflow.booking.repo.FlightInventoryRepository;
import com.aeroflow.common.events.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reacts to flight events: keeps snapshots in sync, cancels/refunds on flight cancellation, emits passenger notices. */
@Service
public class FlightDisruptionHandler {
    private static final Logger log = LoggerFactory.getLogger(FlightDisruptionHandler.class);
    private static final Set<String> NOTIFY = Set.of("DELAYED", "BOARDING", "SCHEDULED");

    private final EventInbox inbox;
    private final BookingRepository bookings;
    private final FlightInventoryRepository inventory;
    private final BookingService bookingService;
    private final FlightClient flightClient;
    private final DomainEventPublisher events;

    public FlightDisruptionHandler(EventInbox inbox, BookingRepository bookings, FlightInventoryRepository inventory,
                                   BookingService bookingService, FlightClient flightClient, DomainEventPublisher events) {
        this.inbox = inbox;
        this.bookings = bookings;
        this.inventory = inventory;
        this.bookingService = bookingService;
        this.flightClient = flightClient;
        this.events = events;
    }

    @Transactional
    public void handle(EventEnvelope env) {
        if (!EventTypes.FLIGHT_STATUS_CHANGED.equals(env.type()) && !EventTypes.FLIGHT_UPDATED.equals(env.type())) return;
        if (!inbox.firstDelivery(env)) return;
        JsonNode p = env.payload();
        UUID flightId = UUID.fromString(p.path("flightId").asText());
        String status = p.path("status").asText();
        flightClient.evict(flightId);
        inventory.findById(flightId).ifPresent(i -> i.sync(status, p.path("capacity").asInt(i.getCapacity())));
        List<Booking> affected = bookings.findByFlightIdAndStatus(flightId, BookingStatus.CONFIRMED);

        if (EventTypes.FLIGHT_UPDATED.equals(env.type())) {
            Instant dep = Instant.parse(p.path("departureTime").asText());
            Instant arr = Instant.parse(p.path("arrivalTime").asText());
            affected.forEach(b -> b.reschedule(dep, arr));
            return;
        }
        if ("CANCELLED".equals(status)) {
            String reason = "Flight cancelled by airline: " + p.path("reason").asText("operational reasons");
            affected.forEach(b -> bookingService.doCancel(b, reason, true));
            log.info("Flight {} cancelled - {} bookings cancelled and refunded", p.path("flightNumber").asText(), affected.size());
            return;
        }
        if (NOTIFY.contains(status)) {
            for (Booking b : affected) {
                Map<String, Object> notice = new LinkedHashMap<>(BookingEvents.payload(b));
                notice.put("flightStatus", status);
                notice.put("previousStatus", p.path("previousStatus").asText());
                notice.put("delayMinutes", p.path("delayMinutes").asInt());
                notice.put("estimatedDepartureTime", p.path("estimatedDepartureTime").asText());
                notice.put("gate", p.path("gate").asText(null));
                notice.put("reason", p.path("reason").asText(null));
                events.publish(Topics.BOOKING, EventTypes.BOOKING_FLIGHT_NOTICE, b.getId().toString(), notice);
            }
        }
    }
}
