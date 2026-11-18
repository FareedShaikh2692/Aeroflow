package com.aeroflow.notification.service;

import com.aeroflow.common.events.EventEnvelope;
import com.aeroflow.common.events.EventInbox;
import com.aeroflow.common.web.PageResponse;
import com.aeroflow.notification.domain.Notification;
import com.aeroflow.notification.repo.NotificationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private final EventInbox inbox;
    private final NotificationRepository repo;
    private final NotificationChannels.EmailSender email;
    private final NotificationChannels.SmsSender sms;

    public NotificationService(EventInbox inbox, NotificationRepository repo, NotificationChannels.EmailSender email,
                               NotificationChannels.SmsSender sms) {
        this.inbox = inbox;
        this.repo = repo;
        this.email = email;
        this.sms = sms;
    }

    public record NotificationView(UUID id, String eventType, String channel, String recipient, String subject,
                                   String body, String pnr, String status, String providerRef, Instant createdAt) {}

    @Transactional
    public int handle(EventEnvelope env) {
        if (!inbox.firstDelivery(env)) return 0;
        var msg = NotificationTemplates.render(env.type(), env.payload());
        if (msg.isEmpty()) return 0;
        JsonNode p = env.payload();
        String pnr = p.path("pnr").isTextual() ? p.path("pnr").asText() : null;
        int sent = 0;
        String to = p.path("contactEmail").asText("");
        if (!to.isBlank()) sent += deliver(env, "EMAIL", to, msg.get().subject(), msg.get().email(), pnr);
        String phone = p.path("contactPhone").asText("");
        if (!phone.isBlank() && !"null".equals(phone)) sent += deliver(env, "SMS", phone, null, msg.get().sms(), pnr);
        return sent;
    }

    private int deliver(EventEnvelope env, String channel, String to, String subject, String body, String pnr) {
        if (repo.existsByEventIdAndChannelAndRecipient(env.eventId(), channel, to)) return 0;
        Notification n = new Notification(env.eventId(), env.type(), channel, to, subject, body, pnr);
        try {
            n.sent("EMAIL".equals(channel) ? email.send(to, subject, body) : sms.send(to, body));
        } catch (RuntimeException e) {
            n.failed(e.getMessage());
        }
        repo.save(n);
        return 1;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationView> search(String q, String channel, String type, Pageable pageable) {
        Specification<Notification> spec = (r, cq, cb) -> cb.conjunction();
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase() + "%";
            spec = spec.and((r, cq, cb) -> cb.or(cb.like(cb.lower(r.get("recipient")), like),
                    cb.like(cb.lower(r.get("pnr")), like), cb.like(cb.lower(r.get("subject")), like)));
        }
        if (channel != null && !channel.isBlank()) spec = spec.and((r, cq, cb) -> cb.equal(r.get("channel"), channel));
        if (type != null && !type.isBlank()) spec = spec.and((r, cq, cb) -> cb.equal(r.get("eventType"), type));
        return PageResponse.of(repo.findAll(spec, pageable), NotificationService::view);
    }

    @Transactional(readOnly = true)
    public List<NotificationView> forRecipient(String email) {
        return repo.findTop50ByRecipientIgnoreCaseOrderByCreatedAtDesc(email).stream().map(NotificationService::view).toList();
    }

    static NotificationView view(Notification n) {
        return new NotificationView(n.getId(), n.getEventType(), n.getChannel(), n.getRecipient(), n.getSubject(), n.getBody(),
                n.getPnr(), n.getStatus(), n.getProviderRef(), n.getCreatedAt());
    }
}
