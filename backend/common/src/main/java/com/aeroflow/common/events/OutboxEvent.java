package com.aeroflow.common.events;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;
import java.time.Instant;
import java.util.UUID;

/** Transactional outbox row: written in the same DB transaction as the business change. */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent implements Persistable<UUID> {
    @Transient private boolean isNew = true;
    @Id
    private UUID id;
    @Column(nullable = false) private String topic;
    @Column(name = "event_type", nullable = false) private String eventType;
    @Column(name = "aggregate_id", nullable = false) private String aggregateId;
    @Column(nullable = false, columnDefinition = "text") private String payload;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "published_at") private Instant publishedAt;
    @Column(nullable = false) private int attempts;
    @Column(name = "last_error", length = 1000) private String lastError;

    protected OutboxEvent() {}

    public OutboxEvent(UUID id, String topic, String eventType, String aggregateId, String payload) {
        this.id = id;
        this.topic = topic;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.createdAt = Instant.now();
    }

    @Override public UUID getId() { return id; }
    @Override public boolean isNew() { return isNew; }
    @PostPersist @PostLoad void markNotNew() { this.isNew = false; }
    public String getTopic() { return topic; }
    public String getEventType() { return eventType; }
    public String getAggregateId() { return aggregateId; }
    public String getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public int getAttempts() { return attempts; }
    public void markPublished() { this.publishedAt = Instant.now(); }
    public void markFailed(String error) {
        this.attempts++;
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 1000));
    }
}
