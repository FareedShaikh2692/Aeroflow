package com.aeroflow.common.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Parses envelopes and guarantees each event id is handled at most once per service. */
@Component
public class EventInbox {
    private final ProcessedEventRepository processed;
    private final ObjectMapper mapper;

    public EventInbox(ProcessedEventRepository processed, ObjectMapper mapper) {
        this.processed = processed;
        this.mapper = mapper;
    }

    public EventEnvelope parse(String json) {
        try {
            return mapper.readValue(json, EventEnvelope.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid event envelope", e);
        }
    }

    /** @return true if this is the first time the event is seen (and records it). */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstDelivery(EventEnvelope env) {
        if (processed.existsById(env.eventId())) return false;
        processed.saveAndFlush(new ProcessedEvent(env.eventId()));
        return true;
    }
}
