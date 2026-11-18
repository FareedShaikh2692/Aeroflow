package com.aeroflow.common.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Records domain events into the outbox. Must run inside the caller's business transaction. */
@Component
public class DomainEventPublisher {
    private final OutboxRepository outbox;
    private final ObjectMapper mapper;
    private final String source;

    public DomainEventPublisher(OutboxRepository outbox, ObjectMapper mapper,
                                @Value("${spring.application.name:aeroflow}") String source) {
        this.outbox = outbox;
        this.mapper = mapper;
        this.source = source;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public EventEnvelope publish(String topic, String type, String aggregateId, Object payload) {
        EventEnvelope env = new EventEnvelope(UUID.randomUUID(), type, source, aggregateId, Instant.now(),
                mapper.valueToTree(payload));
        try {
            outbox.save(new OutboxEvent(env.eventId(), topic, type, aggregateId, mapper.writeValueAsString(env)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise event " + type, e);
        }
        return env;
    }
}
