package com.aeroflow.notification.service;

import com.aeroflow.common.events.EventTypes;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

/** Turns domain events into human-readable messages. */
public final class NotificationTemplates {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("EEE dd MMM yyyy, HH:mm 'UTC'", Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);

    private NotificationTemplates() {}

    public record Message(String subject, String email, String sms) {}

    public static Optional<Message> render(String type, JsonNode p) {
        String pnr = p.path("pnr").asText();
        String flight = p.path("flightNumber").asText() + " " + p.path("origin").asText() + "→" + p.path("destination").asText();
        String when = time(p.path("departureTime").asText());
        return switch (type) {
            case EventTypes.BOOKING_CONFIRMED -> Optional.of(new Message(
                    "Booking confirmed - " + pnr,
                    "Your AeroFlow booking " + pnr + " is confirmed.\n\nFlight: " + flight + "\nDeparture: " + when
                            + "\nPassengers: " + p.path("passengerCount").asInt() + "\nTotal paid: "
                            + p.path("totalAmount").asText() + " " + p.path("currency").asText()
                            + "\n\nOnline check-in opens 48 hours before departure.",
                    "AeroFlow: booking " + pnr + " confirmed. " + flight + " " + when));
            case EventTypes.BOOKING_CANCELLED -> Optional.of(new Message(
                    "Booking cancelled - " + pnr,
                    "Your booking " + pnr + " for " + flight + " has been cancelled.\nReason: " + p.path("reason").asText()
                            + "\nA refund of " + p.path("refundAmount").asText() + " " + p.path("currency").asText()
                            + " has been issued to your original payment method.",
                    "AeroFlow: booking " + pnr + " cancelled. Refund " + p.path("refundAmount").asText() + " "
                            + p.path("currency").asText() + " issued."));
            case EventTypes.BOOKING_FLIGHT_NOTICE -> {
                String status = p.path("flightStatus").asText();
                String detail = switch (status) {
                    case "DELAYED" -> "is delayed by " + p.path("delayMinutes").asInt() + " minutes. New estimated departure: "
                            + time(p.path("estimatedDepartureTime").asText());
                    case "BOARDING" -> "is now boarding" + (p.path("gate").isTextual() ? " at gate " + p.path("gate").asText() : "");
                    default -> "is back on schedule. Departure: " + when;
                };
                String reason = p.path("reason").isTextual() && !p.path("reason").asText().isBlank()
                        ? "\nReason: " + p.path("reason").asText() : "";
                yield Optional.of(new Message("Flight update - " + p.path("flightNumber").asText(),
                        "Flight " + flight + " " + detail + "." + reason + "\nBooking reference: " + pnr,
                        "AeroFlow: " + p.path("flightNumber").asText() + " " + detail));
            }
            case EventTypes.BOOKING_CHECKIN_REMINDER -> Optional.of(new Message(
                    "Check-in is open - " + p.path("flightNumber").asText(),
                    "Online check-in is open for " + flight + " departing " + when + ".\nUse booking reference " + pnr
                            + " and your last name to check in and download your boarding pass.",
                    "AeroFlow: check-in open for " + p.path("flightNumber").asText() + ". Ref " + pnr));
            case EventTypes.CHECKIN_COMPLETED -> Optional.of(new Message(
                    "You're checked in - " + p.path("flightNumber").asText(),
                    "Check-in complete for " + flight + " departing " + when + ".\n" + p.path("passengers").size()
                            + " boarding pass(es) issued for booking " + pnr + ". Boarding starts 45 minutes before departure.",
                    "AeroFlow: checked in on " + p.path("flightNumber").asText() + ". Ref " + pnr));
            default -> Optional.empty();
        };
    }

    private static String time(String iso) {
        try {
            return FMT.format(Instant.parse(iso));
        } catch (Exception e) {
            return iso;
        }
    }
}
