package com.aeroflow.reporting.service;

import com.aeroflow.common.web.PageResponse;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditQueryService {
    private final JdbcTemplate jdbc;

    public AuditQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Parameterised dynamic filter (no string concatenation of user input). */
    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> search(String q, String action, String entityType, ReportService.Range range,
                                                    int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE occurred_at >= ? AND occurred_at < ?");
        List<Object> args = new ArrayList<>(List.of(Timestamp.from(range.from()), Timestamp.from(range.to())));
        if (q != null && !q.isBlank()) {
            where.append(" AND (lower(actor_email) LIKE ? OR lower(entity_id) LIKE ? OR lower(details) LIKE ?)");
            String like = "%" + q.trim().toLowerCase() + "%";
            args.add(like); args.add(like); args.add(like);
        }
        if (action != null && !action.isBlank()) { where.append(" AND action = ?"); args.add(action); }
        if (entityType != null && !entityType.isBlank()) { where.append(" AND entity_type = ?"); args.add(entityType); }
        long total = jdbc.queryForObject("SELECT count(*) FROM audit_log" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) page * size);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, actor_email, actor_roles, action, entity_type, entity_id, "
                + "details, ip_address, source, occurred_at FROM audit_log" + where + " ORDER BY occurred_at DESC LIMIT ? OFFSET ?",
                pageArgs.toArray());
        return new PageResponse<>(rows, page, size, total, (int) Math.ceil(total / (double) size));
    }

    @Transactional(readOnly = true)
    public List<String> actions() {
        return jdbc.queryForList("SELECT DISTINCT action FROM audit_log ORDER BY action", String.class);
    }
}
