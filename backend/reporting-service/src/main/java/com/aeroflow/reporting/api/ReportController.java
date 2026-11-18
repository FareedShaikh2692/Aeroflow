package com.aeroflow.reporting.api;

import com.aeroflow.common.error.ApiException;
import com.aeroflow.common.security.Roles;
import com.aeroflow.common.web.PageResponse;
import com.aeroflow.reporting.service.AuditQueryService;
import com.aeroflow.reporting.service.Csv;
import com.aeroflow.reporting.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Reports & audit")
public class ReportController {
    private final ReportService reports;
    private final AuditQueryService audit;
    private final Clock clock;

    public ReportController(ReportService reports, AuditQueryService audit, Clock clock) {
        this.reports = reports;
        this.audit = audit;
        this.clock = clock;
    }

    @GetMapping("/api/reports/dashboard")
    @PreAuthorize(Roles.IS_STAFF)
    @Operation(summary = "Executive dashboard KPIs, charts, activity and alerts")
    public Map<String, Object> dashboard() {
        return reports.dashboard();
    }

    @GetMapping("/api/reports/{type}")
    @PreAuthorize(Roles.IS_OPS)
    @Operation(summary = "flights | bookings | passengers | revenue report, JSON or CSV (format=csv)")
    public ResponseEntity<?> report(@PathVariable String type,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                    @RequestParam(defaultValue = "json") String format) {
        ReportService.Range range = ReportService.Range.of(from, to, clock);
        if (ChronoUnit.DAYS.between(range.from(), range.to()) > 400) {
            throw ApiException.badRequest("RANGE_TOO_LARGE", "Date range cannot exceed 400 days");
        }
        List<Map<String, Object>> rows = switch (type) {
            case "flights" -> reports.flights(range);
            case "bookings" -> reports.bookings(range);
            case "passengers" -> reports.passengers(range);
            case "revenue" -> reports.revenue(range);
            default -> throw ApiException.notFound("Report", type);
        };
        if ("csv".equalsIgnoreCase(format)) {
            String name = "aeroflow-" + type + "-" + LocalDate.ofInstant(range.from(), java.time.ZoneOffset.UTC) + "_"
                    + LocalDate.ofInstant(range.to().minusSeconds(1), java.time.ZoneOffset.UTC) + ".csv";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                    .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                    .body(Csv.write(rows));
        }
        return ResponseEntity.ok(Map.of("type", type, "from", range.from(), "to", range.to(), "rows", rows,
                "count", rows.size()));
    }

    @GetMapping("/api/audit")
    @PreAuthorize(Roles.IS_ADMIN)
    public PageResponse<Map<String, Object>> audit(@RequestParam(required = false) String q,
                                                   @RequestParam(required = false) String action,
                                                   @RequestParam(required = false) String entityType,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "25") int size) {
        LocalDate f = from == null ? LocalDate.now(clock).minusDays(90) : from;
        return audit.search(q, action, entityType, ReportService.Range.of(f, to, clock), Math.max(0, page),
                Math.min(Math.max(1, size), 100));
    }

    @GetMapping("/api/audit/actions")
    @PreAuthorize(Roles.IS_ADMIN)
    public List<String> auditActions() {
        return audit.actions();
    }
}
