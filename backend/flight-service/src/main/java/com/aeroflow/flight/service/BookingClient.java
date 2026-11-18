package com.aeroflow.flight.service;

import com.aeroflow.common.web.InternalRestClientFactory;
import com.aeroflow.common.web.Resilience;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Synchronous query to booking-service (owner of booking data). */
@Component("flightServiceBookingClient")
public class BookingClient {
    private final RestClient client;

    public BookingClient(InternalRestClientFactory factory, @Value("${aeroflow.services.booking-url}") String url) {
        this.client = factory.create(url);
    }

    public long activeBookingCount(UUID flightId) {
        Map<String, Object> body = Resilience.call("booking-service", () -> client.get()
                .uri("/internal/flights/{id}/booking-count", flightId)
                .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() {}));
        return body == null ? 0 : ((Number) body.getOrDefault("count", 0)).longValue();
    }
}
