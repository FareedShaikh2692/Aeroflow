package com.aeroflow.booking.events;

import com.aeroflow.booking.service.FlightDisruptionHandler;
import com.aeroflow.common.events.EventEnvelope;
import com.aeroflow.common.events.EventInbox;
import com.aeroflow.common.events.Topics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class FlightEventListener {
    private final EventInbox inbox;
    private final FlightDisruptionHandler handler;

    public FlightEventListener(EventInbox inbox, FlightDisruptionHandler handler) {
        this.inbox = inbox;
        this.handler = handler;
    }

    @KafkaListener(topics = Topics.FLIGHT)
    public void onFlightEvent(String message) {
        EventEnvelope env = inbox.parse(message);
        handler.handle(env);
    }
}
