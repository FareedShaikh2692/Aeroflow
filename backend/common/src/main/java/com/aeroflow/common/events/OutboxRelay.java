package com.aeroflow.common.events;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Polls the outbox and publishes pending events through the configured transport (at-least-once delivery). */
@Component
@ConditionalOnProperty(name = "aeroflow.events.relay-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private final OutboxRepository outbox;
    private final EventTransport transport;

    public OutboxRelay(OutboxRepository outbox, EventTransport transport, MeterRegistry meters) {
        this.outbox = outbox;
        this.transport = transport;
        meters.gauge("aeroflow.outbox.pending", outbox, OutboxRepository::countByPublishedAtIsNull);
    }

    @Scheduled(fixedDelayString = "${aeroflow.events.relay-interval-ms:1000}")
    @Transactional
    public void relay() {
        for (OutboxEvent e : outbox.lockBatch(100)) {
            try {
                transport.send(e.getTopic(), e.getAggregateId(), e.getPayload());
                e.markPublished();
            } catch (Exception ex) {
                if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
                e.markFailed(ex.getMessage());
                log.warn("Outbox publish failed for {} ({}), will retry: {}", e.getId(), e.getEventType(), ex.getMessage());
                break; // preserve ordering; retry on next tick
            }
        }
    }
}
