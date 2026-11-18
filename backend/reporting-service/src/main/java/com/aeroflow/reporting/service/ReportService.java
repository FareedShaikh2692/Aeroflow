package com.aeroflow.reporting.service;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-side queries over the projection tables. Everything returned is plain rows for JSON or CSV. */
@Service
public class ReportService {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ReportService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public record Range(Instant from, Instant to) {
        public static Range of(LocalDate from, LocalDate to, Clock clock) {
            LocalDate t = to == null ? LocalDate.now(clock) : to;
            LocalDate f = from == null ? t.minusDays(29) : from;
            if (f.isAfter(t)) { LocalDate x = f; f = t; t = x; }
            return new Range(f.atStartOfDay(ZoneOffset.UTC).toInstant(), t.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard() {
        Instant now = Instant.now(clock);
        Instant dayStart = LocalDate.now(clock).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant dayEnd = dayStart.plus(Duration.ofDays(1));
        Instant d30 = dayStart.minus(Duration.ofDays(29));

        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.putAll(jdbc.queryForMap("""
                SELECT count(*) AS total_flights,
                       count(*) FILTER (WHERE status IN ('BOARDING','DEPARTED')) AS active_flights,
                       count(*) FILTER (WHERE status = 'DELAYED') AS delayed_flights,
                       count(*) FILTER (WHERE status = 'CANCELLED') AS cancelled_flights,
                       count(*) FILTER (WHERE departure_time >= ? AND departure_time < ?) AS flights_today,
                       COALESCE(round(100.0 * sum(booked_seats) FILTER (WHERE departure_time >= ? AND status <> 'CANCELLED')
                         / NULLIF(sum(capacity) FILTER (WHERE departure_time >= ? AND status <> 'CANCELLED'), 0), 1), 0) AS load_factor
                FROM rpt_flights WHERE deleted = FALSE
                """, ts(dayStart), ts(dayEnd), ts(now), ts(now)));
        kpi.putAll(jdbc.queryForMap("""
                SELECT count(*) AS total_bookings,
                       count(*) FILTER (WHERE status = 'CONFIRMED') AS confirmed_bookings,
                       count(*) FILTER (WHERE status = 'CANCELLED') AS cancelled_bookings,
                       count(*) FILTER (WHERE created_at >= ?) AS bookings_today,
                       COALESCE(sum(passengers) FILTER (WHERE status = 'CONFIRMED'), 0) AS passengers_booked,
                       count(DISTINCT lower(contact_email)) AS unique_customers,
                       COALESCE(sum(amount - refund_amount), 0) AS revenue_total,
                       COALESCE(sum(amount - refund_amount) FILTER (WHERE created_at >= ?), 0) AS revenue_30d,
                       COALESCE(sum(amount - refund_amount) FILTER (WHERE synthetic), 0) AS revenue_synthetic
                FROM rpt_bookings
                """, ts(dayStart), ts(d30)));
        kpi.put("registered_passengers", jdbc.queryForObject("SELECT count(*) FROM rpt_users WHERE roles LIKE '%PASSENGER%'", Long.class));
        kpi.put("checkins_today", jdbc.queryForObject(
                "SELECT COALESCE(sum(passengers),0) FROM rpt_checkins WHERE checked_in_at >= ?", Long.class, ts(dayStart)));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kpis", kpi);
        out.put("flightStatus", jdbc.queryForList("""
                SELECT status, count(*) AS count FROM rpt_flights
                WHERE deleted = FALSE AND departure_time >= ? AND departure_time < ? GROUP BY status ORDER BY status
                """, ts(dayStart.minus(Duration.ofDays(3))), ts(dayEnd.plus(Duration.ofDays(3)))));
        out.put("bookingTrend", jdbc.queryForList("""
                SELECT to_char(d, 'YYYY-MM-DD') AS date, COALESCE(count(b.booking_id), 0) AS bookings,
                       COALESCE(sum(b.amount - b.refund_amount), 0) AS revenue,
                       COALESCE(sum(b.passengers), 0) AS passengers
                FROM generate_series(?::date, ?::date, interval '1 day') d
                LEFT JOIN rpt_bookings b ON b.created_at >= d AND b.created_at < d + interval '1 day'
                GROUP BY d ORDER BY d
                """, ts(d30), ts(dayStart)));
        out.put("topRoutes", jdbc.queryForList("""
                SELECT origin || '-' || destination AS route, count(*) AS bookings, sum(passengers) AS passengers,
                       sum(amount - refund_amount) AS revenue
                FROM rpt_bookings WHERE created_at >= ? AND status = 'CONFIRMED'
                GROUP BY 1 ORDER BY revenue DESC LIMIT 6
                """, ts(d30)));
        out.put("recentActivity", jdbc.queryForList(
                "SELECT kind, message, ref, occurred_at FROM rpt_activity ORDER BY occurred_at DESC LIMIT 12"));
        out.put("alerts", alerts(now));
        out.put("syntheticDataIncluded", ((Number) kpi.get("revenue_synthetic")).doubleValue() > 0);
        out.put("generatedAt", now);
        return out;
    }

    private List<Map<String, Object>> alerts(Instant now) {
        List<Map<String, Object>> alerts = new ArrayList<>();
        jdbc.queryForList("""
                SELECT flight_number, origin, destination, delay_minutes, departure_time FROM rpt_flights
                WHERE status = 'DELAYED' AND deleted = FALSE AND departure_time BETWEEN ? AND ? ORDER BY departure_time LIMIT 5
                """, ts(now.minus(Duration.ofHours(2))), ts(now.plus(Duration.ofHours(24)))).forEach(r -> alerts.add(alert(
                ((Number) r.get("delay_minutes")).intValue() >= 90 ? "critical" : "warning",
                r.get("flight_number") + " delayed " + r.get("delay_minutes") + " min",
                r.get("origin") + " → " + r.get("destination") + ", scheduled " + r.get("departure_time"))));
        jdbc.queryForList("""
                SELECT flight_number, origin, destination, departure_time FROM rpt_flights
                WHERE status = 'CANCELLED' AND deleted = FALSE AND departure_time BETWEEN ? AND ? ORDER BY departure_time LIMIT 5
                """, ts(now.minus(Duration.ofHours(12))), ts(now.plus(Duration.ofHours(72)))).forEach(r -> alerts.add(alert("critical",
                r.get("flight_number") + " cancelled", r.get("origin") + " → " + r.get("destination") + " on " + r.get("departure_time"))));
        jdbc.queryForList("""
                SELECT flight_number, origin, destination, booked_seats, capacity FROM rpt_flights
                WHERE deleted = FALSE AND status IN ('SCHEDULED','DELAYED') AND departure_time BETWEEN ? AND ?
                  AND capacity > 0 AND booked_seats::numeric / capacity >= 0.9 LIMIT 5
                """, ts(now), ts(now.plus(Duration.ofDays(7)))).forEach(r -> alerts.add(alert("info",
                r.get("flight_number") + " nearly full", r.get("booked_seats") + "/" + r.get("capacity") + " seats sold")));
        return alerts;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> flights(Range r) {
        return jdbc.queryForList("""
                SELECT flight_number, origin, destination, departure_time, status, delay_minutes, capacity, booked_seats,
                       round(100.0 * booked_seats / NULLIF(capacity, 0), 1) AS load_factor
                FROM rpt_flights WHERE deleted = FALSE AND departure_time >= ? AND departure_time < ?
                ORDER BY departure_time
                """, ts(r.from()), ts(r.to()));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> bookings(Range r) {
        return jdbc.queryForList("""
                SELECT pnr, flight_number, origin, destination, departure_time, contact_email, status, passengers, amount,
                       refund_amount, currency, channel, created_at, cancelled_at, synthetic
                FROM rpt_bookings WHERE created_at >= ? AND created_at < ? ORDER BY created_at DESC
                """, ts(r.from()), ts(r.to()));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> passengers(Range r) {
        return jdbc.queryForList("""
                SELECT lower(contact_email) AS customer_email, count(*) AS bookings,
                       count(*) FILTER (WHERE status = 'CONFIRMED') AS confirmed, sum(passengers) AS passengers,
                       sum(amount - refund_amount) AS net_spend, min(created_at) AS first_booking, max(created_at) AS last_booking,
                       bool_or(synthetic) AS synthetic
                FROM rpt_bookings WHERE created_at >= ? AND created_at < ?
                GROUP BY 1 ORDER BY net_spend DESC
                """, ts(r.from()), ts(r.to()));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> revenue(Range r) {
        return jdbc.queryForList("""
                SELECT to_char(date_trunc('day', created_at), 'YYYY-MM-DD') AS date, origin || '-' || destination AS route,
                       count(*) AS bookings, sum(passengers) AS passengers, sum(amount) AS gross,
                       sum(refund_amount) AS refunds, sum(amount - refund_amount) AS net, max(currency) AS currency,
                       bool_or(synthetic) AS synthetic
                FROM rpt_bookings WHERE created_at >= ? AND created_at < ?
                GROUP BY 1, 2 ORDER BY 1 DESC, net DESC
                """, ts(r.from()), ts(r.to()));
    }

    private static Timestamp ts(Instant i) { return Timestamp.from(i); }

    private static Map<String, Object> alert(String severity, String title, String detail) {
        return Map.of("severity", severity, "title", title, "detail", detail);
    }
}
