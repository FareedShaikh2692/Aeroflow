package com.aeroflow.common.events;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
    /** SKIP LOCKED lets several replicas relay concurrently without double-publishing. */
    @Query(value = "SELECT * FROM outbox_events WHERE published_at IS NULL AND attempts < 20 "
            + "ORDER BY created_at LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEvent> lockBatch(int limit);

    long countByPublishedAtIsNull();
}
