package com.aeroflow.booking.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {
    @Id private UUID id;
    @Column(name = "booking_id", nullable = false) private UUID bookingId;
    @Column(nullable = false) private String type; // CHARGE | REFUND
    @Column(nullable = false) private BigDecimal amount;
    @Column(nullable = false) private String currency;
    @Column(nullable = false) private String status;
    @Column(name = "provider_ref", nullable = false) private String providerRef;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected Payment() {}

    public Payment(UUID bookingId, String type, BigDecimal amount, String currency, String status, String providerRef) {
        this.id = UUID.randomUUID();
        this.bookingId = bookingId;
        this.type = type;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.providerRef = providerRef;
        this.createdAt = Instant.now();
    }

    public String getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public String getStatus() { return status; }
    public String getProviderRef() { return providerRef; }
    public Instant getCreatedAt() { return createdAt; }
}
