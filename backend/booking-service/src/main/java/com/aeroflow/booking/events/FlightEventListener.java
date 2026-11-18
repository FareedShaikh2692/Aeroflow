package com.aeroflow.booking.events;

import com.aeroflow.booking.service.FlightDisruptionHandler;
import com.aeroflow.common.events.EventEnvelope;
import com.aeroflow.common.events.EventInbox;
import com.aeroflow.common.events.EventSubscriber;
import java.util.List;
import com.aeroflow.common.events.Topics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class FlightEventListener implements EventSubscriber {
    private final EventInbox inbox;
    private final FlightDisruptionHandler handler;

    public FlightEventListener(EventInbox inbox, FlightDisruptionHandler handler) {
        this.inbox = inbox;
        this.handler = handler;
    }

    @Override
    public List<String> topics() {
        return List.of(Topics.FLIGHT);
    }

    @KafkaListener(topics = Topics.FLIGHT, autoStartup = EventSubscriber.KAFKA_AUTO_STARTUP)
    @Override
    public void handle(String message) {
        EventEnvelope env = inbox.parse(message);
        handler.handle(env);
    }
}
