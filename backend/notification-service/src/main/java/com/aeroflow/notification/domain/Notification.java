package com.aeroflow.notification.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {
    @Id private UUID id;
    @Column(name = "event_id", nullable = false) private UUID eventId;
    @Column(name = "event_type", nullable = false) private String eventType;
    @Column(nullable = false) private String channel;
    @Column(nullable = false) private String recipient;
    private String subject;
    @Column(nullable = false, columnDefinition = "text") private String body;
    private String pnr;
    @Column(nullable = false) private String status;
    @Column(name = "provider_ref") private String providerRef;
    private String error;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected Notification() {}

    public Notification(UUID eventId, String eventType, String channel, String recipient, String subject, String body, String pnr) {
        this.id = UUID.randomUUID();
        this.eventId = eventId;
        this.eventType = eventType;
        this.channel = channel;
        this.recipient = recipient;
        this.subject = subject;
        this.body = body;
        this.pnr = pnr;
        this.status = "PENDING";
        this.createdAt = Instant.now();
    }

    public void sent(String ref) { status = "SENT"; providerRef = ref; }
    public void failed(String err) { status = "FAILED"; error = err == null ? null : err.substring(0, Math.min(500, err.length())); }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public String getChannel() { return channel; }
    public String getRecipient() { return recipient; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public String getPnr() { return pnr; }
    public String getStatus() { return status; }
    public String getProviderRef() { return providerRef; }
    public String getError() { return error; }
    public Instant getCreatedAt() { return createdAt; }
}
