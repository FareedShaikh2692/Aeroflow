package com.aeroflow.common.events;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/** Wire format of every domain event published to Kafka. */
public record EventEnvelope(UUID eventId, String type, String source, String aggregateId, Instant occurredAt,
                            JsonNode payload) {}
