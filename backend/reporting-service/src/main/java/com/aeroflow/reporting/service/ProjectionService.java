package com.aeroflow.reporting.service;

import com.aeroflow.common.events.EventEnvelope;
import com.aeroflow.common.events.EventInbox;
import com.aeroflow.common.events.EventTypes;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Applies every domain event to the reporting read model (CQRS projection). Idempotent via the inbox. */
@Service
public class ProjectionService {
    private final EventInbox inbox;
    private final JdbcTemplate jdbc;

    public ProjectionService(EventInbox inbox, JdbcTemplate jdbc) {
        this.inbox = inbox;
        this.jdbc = jdbc;
    }

    @Transactional
    public void apply(EventEnvelope env) {
        if (!inbox.firstDelivery(env)) return;
        JsonNode p = env.payload();
        switch (env.type()) {
            case EventTypes.FLIGHT_CREATED, EventTypes.FLIGHT_UPDATED, EventTypes.FLIGHT_STATUS_CHANGED -> {
                jdbc.update("""
                        INSERT INTO rpt_flights (flight_id, flight_number, origin, destination, departure_time, status,
                                                 delay_minutes, capacity, updated_at)
                        VALUES (?,?,?,?,?,?,?,?,now())
                        ON CONFLICT (flight_id) DO UPDATE SET flight_number = EXCLUDED.flight_number, origin = EXCLUDED.origin,
                          destination = EXCLUDED.destination, departure_time = EXCLUDED.departure_time, status = EXCLUDED.status,
                          delay_minutes = EXCLUDED.delay_minutes, capacity = EXCLUDED.capacity, updated_at = now()
                        """, uuid(p, "flightId"), p.path("flightNumber").asText(), p.path("origin").asText(),
                        p.path("destination").asText(), ts(p, "departureTime"), p.path("status").asText(),
                        p.path("delayMinutes").asInt(), p.path("capacity").asInt());
                if (EventTypes.FLIGHT_STATUS_CHANGED.equals(env.type())) {
                    String s = p.path("status").asText();
                    String msg = p.path("flightNumber").asText() + " " + p.path("origin").asText() + "→"
                            + p.path("destination").asText() + " is now " + s
                            + ("DELAYED".equals(s) ? " (+" + p.path("delayMinutes").asInt() + " min)" : "");
                    activity("FLIGHT_" + s, msg, p.path("flightNumber").asText(), env.occurredAt());
                }
            }
            case EventTypes.FLIGHT_DELETED -> jdbc.update("UPDATE rpt_flights SET deleted = TRUE WHERE flight_id = ?", uuid(p, "flightId"));
            case EventTypes.BOOKING_CONFIRMED -> {
                int n = jdbc.update("""
                        INSERT INTO rpt_bookings (booking_id, pnr, flight_id, flight_number, origin, destination, departure_time,
                          contact_email, status, passengers, amount, currency, channel, created_at)
                        VALUES (?,?,?,?,?,?,?,?, 'CONFIRMED', ?,?,?,?,?) ON CONFLICT (booking_id) DO NOTHING
                        """, uuid(p, "bookingId"), p.path("pnr").asText(), uuid(p, "flightId"), p.path("flightNumber").asText(),
                        p.path("origin").asText(), p.path("destination").asText(), ts(p, "departureTime"),
                        p.path("contactEmail").asText().toLowerCase(), p.path("passengerCount").asInt(),
                        p.path("totalAmount").decimalValue(), p.path("currency").asText(), p.path("channel").asText("WEB"),
                        ts(p, "createdAt"));
                if (n > 0) jdbc.update("UPDATE rpt_flights SET booked_seats = booked_seats + ? WHERE flight_id = ?",
                        p.path("passengerCount").asInt(), uuid(p, "flightId"));
                activity("BOOKING_CONFIRMED", "New booking " + p.path("pnr").asText() + " on " + p.path("flightNumber").asText()
                        + " · " + p.path("passengerCount").asInt() + " pax · " + p.path("totalAmount").asText() + " "
                        + p.path("currency").asText(), p.path("pnr").asText(), env.occurredAt());
            }
            case EventTypes.BOOKING_CANCELLED -> {
                int n = jdbc.update("""
                        UPDATE rpt_bookings SET status = 'CANCELLED', cancelled_at = ?, refund_amount = ?
                        WHERE booking_id = ? AND status <> 'CANCELLED'
                        """, Timestamp.from(env.occurredAt()), p.path("refundAmount").decimalValue(), uuid(p, "bookingId"));
                if (n > 0) jdbc.update("UPDATE rpt_flights SET booked_seats = GREATEST(0, booked_seats - ?) WHERE flight_id = ?",
                        p.path("passengerCount").asInt(), uuid(p, "flightId"));
                activity("BOOKING_CANCELLED", "Booking " + p.path("pnr").asText() + " cancelled"
                        + (p.path("cancelledByAirline").asBoolean() ? " by airline" : ""), p.path("pnr").asText(), env.occurredAt());
            }
            case EventTypes.CHECKIN_COMPLETED -> {
                jdbc.update("""
                        INSERT INTO rpt_checkins (event_id, booking_id, flight_id, passengers, channel, checked_in_at)
                        VALUES (?,?,?,?,?,?) ON CONFLICT DO NOTHING
                        """, env.eventId(), uuid(p, "bookingId"), uuid(p, "flightId"), p.path("passengers").size(),
                        p.path("channel").asText("ONLINE"), Timestamp.from(env.occurredAt()));
                activity("CHECKIN_COMPLETED", p.path("passengers").size() + " passenger(s) checked in on "
                        + p.path("flightNumber").asText() + " (" + p.path("pnr").asText() + ")", p.path("pnr").asText(), env.occurredAt());
            }
            case EventTypes.USER_REGISTERED -> jdbc.update("""
                    INSERT INTO rpt_users (user_id, email, roles, registered_at) VALUES (?,?,?,?) ON CONFLICT DO NOTHING
                    """, uuid(p, "userId"), p.path("email").asText(), p.path("roles").toString(), Timestamp.from(env.occurredAt()));
            case EventTypes.AUDIT_RECORDED -> jdbc.update("""
                    INSERT INTO audit_log (id, actor_id, actor_email, actor_roles, action, entity_type, entity_id, details,
                      ip_address, source, occurred_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)
                    """, env.eventId(), text(p, "actorId"), p.path("actorEmail").asText("system"), text(p, "actorRoles"),
                    p.path("action").asText(), p.path("entityType").asText(), text(p, "entityId"), text(p, "details"),
                    text(p, "ipAddress"), env.source(), ts(p, "occurredAt"));
            default -> { }
        }
    }

    private void activity(String kind, String message, String ref, Instant at) {
        jdbc.update("INSERT INTO rpt_activity (id, kind, message, ref, occurred_at) VALUES (?,?,?,?,?)",
                UUID.randomUUID(), kind, message.length() > 300 ? message.substring(0, 300) : message, ref, Timestamp.from(at));
    }

    private static UUID uuid(JsonNode p, String f) {
        String v = p.path(f).asText(null);
        return v == null || v.isBlank() ? null : UUID.fromString(v);
    }

    private static Timestamp ts(JsonNode p, String f) {
        String v = p.path(f).asText(null);
        return Timestamp.from(v == null ? Instant.now() : Instant.parse(v));
    }

    private static String text(JsonNode p, String f) {
        JsonNode n = p.path(f);
        return n.isMissingNode() || n.isNull() ? null : n.isTextual() ? n.asText() : n.toString();
    }
}
