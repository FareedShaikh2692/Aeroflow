package com.aeroflow.passenger.events;

import com.aeroflow.common.events.EventInbox;
import com.aeroflow.common.events.Topics;
import com.aeroflow.passenger.service.PassengerEventHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PassengerEventListener {
    private final EventInbox inbox;
    private final PassengerEventHandler handler;

    public PassengerEventListener(EventInbox inbox, PassengerEventHandler handler) {
        this.inbox = inbox;
        this.handler = handler;
    }

    @KafkaListener(topics = {Topics.USER, Topics.BOOKING, Topics.CHECKIN})
    public void on(String message) {
        handler.handle(inbox.parse(message));
    }
}
