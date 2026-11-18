package com.aeroflow.booking.service;

import com.aeroflow.common.error.ApiException;
import com.aeroflow.common.web.InternalRestClientFactory;
import com.aeroflow.common.web.Resilience;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/** Synchronous read of authoritative flight data from flight-service (cached, retried, circuit-broken). */
@Component
public class FlightClient {
    private final RestClient client;

    public FlightClient(InternalRestClientFactory factory, @Value("${aeroflow.services.flight-url}") String url) {
        this.client = factory.create(url);
    }

    public record Airport(String code, String city) implements Serializable {}
    public record Layout(int businessRows, String businessLayout, int economyRows, String economyLayout) implements Serializable {}
    public record Aircraft(String model, String registration, Layout layout) implements Serializable {}
    public record Flight(UUID id, String flightNumber, Airport origin, Airport destination, Aircraft aircraft,
                         Instant departureTime, Instant arrivalTime, String status, int delayMinutes, String gate,
                         String terminal, BigDecimal economyFare, BigDecimal businessFare, String currency,
                         int capacity) implements Serializable {
        public SeatLayout seatLayout() {
            Layout l = aircraft.layout();
            return new SeatLayout(l.businessRows(), l.businessLayout(), l.economyRows(), l.economyLayout());
        }
    }

    @Cacheable(value = "flightLayout", key = "#id")
    public Flight get(UUID id) {
        try {
            return Resilience.call("flight-service", () -> client.get().uri("/internal/flights/{id}", id)
                    .retrieve().body(Flight.class));
        } catch (HttpClientErrorException.NotFound e) {
            throw ApiException.notFound("Flight", id);
        }
    }

    @CacheEvict(value = "flightLayout", key = "#id")
    public void evict(UUID id) {
        // cache eviction only
    }
}
