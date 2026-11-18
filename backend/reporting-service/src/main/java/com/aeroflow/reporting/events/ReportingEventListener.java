package com.aeroflow.reporting.events;

import com.aeroflow.common.events.EventInbox;
import com.aeroflow.common.events.Topics;
import com.aeroflow.reporting.service.ProjectionService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ReportingEventListener {
    private final EventInbox inbox;
    private final ProjectionService projection;

    public ReportingEventListener(EventInbox inbox, ProjectionService projection) {
        this.inbox = inbox;
        this.projection = projection;
    }

    @KafkaListener(topics = {Topics.USER, Topics.FLIGHT, Topics.BOOKING, Topics.CHECKIN, Topics.AUDIT})
    public void on(String message) {
        projection.apply(inbox.parse(message));
    }
}
