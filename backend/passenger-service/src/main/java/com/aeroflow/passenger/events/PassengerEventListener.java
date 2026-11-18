package com.aeroflow.passenger.events;

import com.aeroflow.common.events.EventInbox;
import com.aeroflow.common.events.EventSubscriber;
import java.util.List;
import com.aeroflow.common.events.Topics;
import com.aeroflow.passenger.service.PassengerEventHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PassengerEventListener implements EventSubscriber {
    private final EventInbox inbox;
    private final PassengerEventHandler handler;

    public PassengerEventListener(EventInbox inbox, PassengerEventHandler handler) {
        this.inbox = inbox;
        this.handler = handler;
    }

    @Override
    public List<String> topics() {
        return List.of(Topics.USER, Topics.BOOKING, Topics.CHECKIN);
    }

    @KafkaListener(topics = {Topics.USER, Topics.BOOKING, Topics.CHECKIN}, autoStartup = EventSubscriber.KAFKA_AUTO_STARTUP)
    @Override
    public void handle(String message) {
        handler.handle(inbox.parse(message));
    }
}
