package com.aeroflow.reporting.events;

import com.aeroflow.common.events.EventInbox;
import com.aeroflow.common.events.EventSubscriber;
import java.util.List;
import com.aeroflow.common.events.Topics;
import com.aeroflow.reporting.service.ProjectionService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ReportingEventListener implements EventSubscriber {
    private final EventInbox inbox;
    private final ProjectionService projection;

    public ReportingEventListener(EventInbox inbox, ProjectionService projection) {
        this.inbox = inbox;
        this.projection = projection;
    }

    @Override
    public List<String> topics() {
        return List.of(Topics.USER, Topics.FLIGHT, Topics.BOOKING, Topics.CHECKIN, Topics.AUDIT);
    }

    @KafkaListener(topics = {Topics.USER, Topics.FLIGHT, Topics.BOOKING, Topics.CHECKIN, Topics.AUDIT}, autoStartup = EventSubscriber.KAFKA_AUTO_STARTUP)
    @Override
    public void handle(String message) {
        projection.apply(inbox.parse(message));
    }
}
