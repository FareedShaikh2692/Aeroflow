package com.aeroflow.notification.events;

import com.aeroflow.common.events.EventInbox;
import com.aeroflow.common.events.EventSubscriber;
import java.util.List;
import com.aeroflow.common.events.Topics;
import com.aeroflow.notification.service.NotificationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventListener implements EventSubscriber {
    private final EventInbox inbox;
    private final NotificationService service;

    public NotificationEventListener(EventInbox inbox, NotificationService service) {
        this.inbox = inbox;
        this.service = service;
    }

    @Override
    public List<String> topics() {
        return List.of(Topics.BOOKING, Topics.CHECKIN);
    }

    @KafkaListener(topics = {Topics.BOOKING, Topics.CHECKIN}, autoStartup = EventSubscriber.KAFKA_AUTO_STARTUP)
    @Override
    public void handle(String message) {
        service.handle(inbox.parse(message));
    }
}
