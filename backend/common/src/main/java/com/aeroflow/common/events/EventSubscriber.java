package com.aeroflow.common.events;

import java.util.List;

/**
 * A consumer of domain events, independent of the transport. With Kafka the implementing class's
 * {@code @KafkaListener} method is used; with the PostgreSQL bus, {@link PostgresEventBus} calls {@link #handle}.
 */
public interface EventSubscriber {
    /** SpEL for {@code @KafkaListener(autoStartup = ...)}: Kafka containers only start when transport=kafka. */
    String KAFKA_AUTO_STARTUP = "#{'${aeroflow.events.transport:kafka}' == 'kafka'}";

    List<String> topics();

    /** Stable consumer name; with the PostgreSQL bus each subscriber is its own consumer group. */
    default String consumerName() {
        return getClass().getSimpleName();
    }

    void handle(String message);
}
