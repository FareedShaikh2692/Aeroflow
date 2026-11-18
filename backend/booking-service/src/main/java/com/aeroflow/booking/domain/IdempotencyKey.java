package com.aeroflow.booking.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey {
    @Id @Column(name = "idem_key") private String key;
    @Column(name = "request_hash", nullable = false) private String requestHash;
    @Column(name = "booking_id", nullable = false) private UUID bookingId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected IdempotencyKey() {}

    public IdempotencyKey(String key, String requestHash, UUID bookingId) {
        this.key = key;
        this.requestHash = requestHash;
        this.bookingId = bookingId;
        this.createdAt = Instant.now();
    }

    public String getRequestHash() { return requestHash; }
    public UUID getBookingId() { return bookingId; }
}
