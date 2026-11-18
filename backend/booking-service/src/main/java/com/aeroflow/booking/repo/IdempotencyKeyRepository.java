package com.aeroflow.booking.repo;

import com.aeroflow.booking.domain.IdempotencyKey;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {
    @Modifying
    @Query("delete from IdempotencyKey k where k.createdAt < :cutoff")
    int deleteOlderThan(Instant cutoff);
}
