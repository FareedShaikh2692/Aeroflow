package com.aeroflow.common.audit;

import com.aeroflow.common.events.DomainEventPublisher;
import com.aeroflow.common.events.EventTypes;
import com.aeroflow.common.events.Topics;
import com.aeroflow.common.security.AuthenticatedUser;
import com.aeroflow.common.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Emits audit records (via the outbox) for important administrative actions. */
@Service
public class AuditService {
    private final DomainEventPublisher events;

    public AuditService(DomainEventPublisher events) {
        this.events = events;
    }

    public record AuditRecord(String actorId, String actorEmail, String actorRoles, String action, String entityType,
                              String entityId, String details, String ipAddress, Instant occurredAt) {}

    public void record(String action, String entityType, String entityId, String details) {
        AuthenticatedUser u = CurrentUser.get().orElse(null);
        record(u == null ? null : u.id().toString(), u == null ? "anonymous" : u.email(),
                u == null ? "" : String.join(",", u.roles()), action, entityType, entityId, details);
    }

    public void record(String actorId, String actorEmail, String actorRoles, String action, String entityType,
                       String entityId, String details) {
        AuditRecord rec = new AuditRecord(actorId, actorEmail, actorRoles, action, entityType, entityId,
                details, clientIp(), Instant.now());
        events.publish(Topics.AUDIT, EventTypes.AUDIT_RECORDED, entityType + ":" + entityId, rec);
    }

    public static Map<String, Object> details(Object... kv) {
        java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(String.valueOf(kv[i]), kv[i + 1]);
        return m;
    }

    private static String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest req = attrs.getRequest();
            String fwd = req.getHeader("X-Forwarded-For");
            return fwd != null && !fwd.isBlank() ? fwd.split(",")[0].trim() : req.getRemoteAddr();
        }
        return null;
    }
}
