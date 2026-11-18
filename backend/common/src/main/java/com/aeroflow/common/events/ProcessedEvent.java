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

/** Inbox de-duplication record that makes consumers idempotent. */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent implements Persistable<UUID> {
    @Transient private boolean isNew = true;
    @Id @Column(name = "event_id") private UUID eventId;
    @Column(name = "processed_at", nullable = false) private Instant processedAt;

    protected ProcessedEvent() {}
    public ProcessedEvent(UUID eventId) { this.eventId = eventId; this.processedAt = Instant.now(); }
    public UUID getEventId() { return eventId; }
    @Override public UUID getId() { return eventId; }
    @Override public boolean isNew() { return isNew; }
    @PostPersist @PostLoad void markNotNew() { this.isNew = false; }
}
