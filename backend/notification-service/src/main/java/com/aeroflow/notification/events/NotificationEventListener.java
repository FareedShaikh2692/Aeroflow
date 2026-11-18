package com.aeroflow.notification.events;

import com.aeroflow.common.events.EventInbox;
import com.aeroflow.common.events.Topics;
import com.aeroflow.notification.service.NotificationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventListener {
    private final EventInbox inbox;
    private final NotificationService service;

    public NotificationEventListener(EventInbox inbox, NotificationService service) {
        this.inbox = inbox;
        this.service = service;
    }

    @KafkaListener(topics = {Topics.BOOKING, Topics.CHECKIN})
    public void on(String message) {
        service.handle(inbox.parse(message));
    }
}
