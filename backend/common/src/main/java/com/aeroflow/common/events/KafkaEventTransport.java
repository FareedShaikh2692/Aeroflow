package com.aeroflow.common.events;

import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;

/** Kafka transport (aeroflow.events.transport=kafka, the default). */
@Configuration
@ConditionalOnProperty(name = "aeroflow.events.transport", havingValue = "kafka", matchIfMissing = true)
public class KafkaEventTransport {

    @Bean
    EventTransport kafkaTransport(KafkaTemplate<String, String> kafka) {
        return (topic, key, payload) -> kafka.send(topic, key, payload).get(5, TimeUnit.SECONDS);
    }

    /** Main topics + dead-letter topics (managed brokers usually disable auto-creation). */
    @Bean
    KafkaAdmin.NewTopics aeroflowTopics(@Value("${aeroflow.events.topic-partitions:3}") int partitions,
                                        @Value("${aeroflow.events.topic-replicas:1}") int replicas) {
        return new KafkaAdmin.NewTopics(Topics.ALL.stream()
                .flatMap(t -> java.util.stream.Stream.of(t, t + ".DLT"))
                .map(t -> TopicBuilder.name(t).partitions(partitions).replicas(replicas).build())
                .toArray(NewTopic[]::new));
    }
}
