package com.aeroflow.checkin.service;

import com.aeroflow.common.error.ApiException;
import com.aeroflow.common.web.InternalRestClientFactory;
import com.aeroflow.common.web.Resilience;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/** Calls booking-service internal endpoints and maps its 4xx errors back to client errors. */
@Component("checkinServiceBookingClient")
public class BookingClient {
    private final RestClient client;
    private final ObjectMapper mapper;

    public BookingClient(InternalRestClientFactory factory, ObjectMapper mapper,
                         @Value("${aeroflow.services.booking-url}") String url) {
        this.client = factory.create(url);
        this.mapper = mapper;
    }

    public record Passenger(UUID id, String firstName, String lastName, String cabin, String seatNumber,
                            boolean checkedIn, String email) {}
    public record Booking(UUID id, String pnr, UUID flightId, String flightNumber, String origin, String destination,
                          Instant departureTime, Instant arrivalTime, String status, BigDecimal totalAmount,
                          String currency, String contactEmail, List<Passenger> passengers) {}

    public Booking retrieve(String pnr, String lastName) {
        return call(() -> client.post().uri("/internal/bookings/retrieve")
                .body(Map.of("pnr", pnr, "lastName", lastName)).retrieve().body(Booking.class));
    }

    public Booking changeSeat(String pnr, UUID passengerId, String seat) {
        return call(() -> client.put().uri("/internal/bookings/{pnr}/passengers/{pid}/seat", pnr, passengerId)
                .body(Map.of("seatNumber", seat)).retrieve().body(Booking.class));
    }

    public Booking markCheckedIn(String pnr, List<UUID> passengerIds) {
        return call(() -> client.post().uri("/internal/bookings/{pnr}/check-in", pnr)
                .body(Map.of("passengerIds", passengerIds)).retrieve().body(Booking.class));
    }

    private <T> T call(java.util.function.Supplier<T> s) {
        try {
            return Resilience.call("booking-service", s);
        } catch (HttpClientErrorException e) {
            String code = "UPSTREAM_REJECTED";
            String message = "Booking service rejected the request";
            try {
                var node = mapper.readTree(e.getResponseBodyAsString());
                code = node.path("code").asText(code);
                message = node.path("message").asText(message);
            } catch (Exception ignored) {
                // keep defaults
            }
            throw new ApiException(org.springframework.http.HttpStatus.valueOf(e.getStatusCode().value()), code, message);
        }
    }
}
