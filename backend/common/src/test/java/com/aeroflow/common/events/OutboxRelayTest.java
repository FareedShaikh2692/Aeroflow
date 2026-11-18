package com.aeroflow.common.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

class OutboxRelayTest {
    final OutboxRepository repo = mock(OutboxRepository.class);
    @SuppressWarnings("unchecked")
    final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    final OutboxRelay relay = new OutboxRelay(repo, kafka, new SimpleMeterRegistry());

    OutboxEvent event(String key) {
        return new OutboxEvent(UUID.randomUUID(), Topics.BOOKING, EventTypes.BOOKING_CONFIRMED, key, "{}");
    }

    @Test
    void publishesInOrderAndMarksEventsPublished() {
        OutboxEvent a = event("a"), b = event("b");
        when(repo.lockBatch(100)).thenReturn(List.of(a, b));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        relay.relay();
        assertThat(a.getPublishedAt()).isNotNull();
        assertThat(b.getPublishedAt()).isNotNull();
        var order = inOrder(kafka);
        order.verify(kafka).send(Topics.BOOKING, "a", "{}");
        order.verify(kafka).send(Topics.BOOKING, "b", "{}");
    }

    @Test
    void stopsBatchOnFailureToPreserveOrderingAndRecordsAttempt() {
        OutboxEvent a = event("a"), b = event("b");
        when(repo.lockBatch(100)).thenReturn(List.of(a, b));
        when(kafka.send(anyString(), eq("a"), anyString())).thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));
        relay.relay();
        assertThat(a.getPublishedAt()).isNull();
        assertThat(a.getAttempts()).isEqualTo(1);
        verify(kafka, never()).send(anyString(), eq("b"), anyString());
    }

    @Test
    void publisherWrapsPayloadInEnvelopeAndInboxDeduplicates() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        DomainEventPublisher pub = new DomainEventPublisher(repo, mapper, "test-service");
        EventEnvelope env = pub.publish(Topics.FLIGHT, EventTypes.FLIGHT_CREATED, "f-1", Map.of("flightNumber", "AF100"));
        verify(repo).save(argThat(e -> e.getTopic().equals(Topics.FLIGHT) && e.getPayload().contains("AF100")));
        assertThat(env.source()).isEqualTo("test-service");

        ProcessedEventRepository processed = mock(ProcessedEventRepository.class);
        EventInbox inbox = new EventInbox(processed, mapper);
        EventEnvelope parsed = inbox.parse(mapper.writeValueAsString(env));
        assertThat(parsed.payload().path("flightNumber").asText()).isEqualTo("AF100");
        when(processed.existsById(env.eventId())).thenReturn(false, true);
        assertThat(inbox.firstDelivery(parsed)).isTrue();
        assertThat(inbox.firstDelivery(parsed)).isFalse();
    }
}
