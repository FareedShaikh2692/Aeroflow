package com.aeroflow.booking.service;

import com.aeroflow.booking.domain.Booking;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Event contract for booking.* events. */
public final class BookingEvents {
    private BookingEvents() {}

    public static Map<String, Object> payload(Booking b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("bookingId", b.getId().toString());
        m.put("pnr", b.getPnr());
        m.put("flightId", b.getFlightId().toString());
        m.put("flightNumber", b.getFlightNumber());
        m.put("origin", b.getOrigin());
        m.put("destination", b.getDestination());
        m.put("departureTime", b.getDepartureTime().toString());
        m.put("status", b.getStatus().name());
        m.put("userId", b.getUserId() == null ? null : b.getUserId().toString());
        m.put("contactEmail", b.getContactEmail());
        m.put("contactPhone", b.getContactPhone());
        m.put("totalAmount", b.getTotalAmount());
        m.put("currency", b.getCurrency());
        m.put("channel", b.getChannel());
        m.put("createdAt", b.getCreatedAt().toString());
        m.put("passengerCount", b.getPassengers().size());
        List<Map<String, Object>> pax = b.getPassengers().stream().map(p -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("passengerId", p.getId().toString());
            x.put("firstName", p.getFirstName());
            x.put("lastName", p.getLastName());
            x.put("email", p.getEmail());
            x.put("cabin", p.getCabin().name());
            x.put("seatNumber", p.getSeatNumber());
            x.put("fare", p.getFare());
            return x;
        }).toList();
        m.put("passengers", pax);
        return m;
    }
}
