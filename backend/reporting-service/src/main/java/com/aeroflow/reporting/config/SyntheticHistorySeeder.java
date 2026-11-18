package com.aeroflow.reporting.config;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds 90 days of SYNTHETIC historical bookings (flag synthetic = true) so the executive dashboard and
 * revenue reports have meaningful demo trends. Real bookings made in the app are added on top and are never synthetic.
 */
@Component
@ConditionalOnProperty(name = "aeroflow.seed.synthetic-history", havingValue = "true")
public class SyntheticHistorySeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SyntheticHistorySeeder.class);
    private static final List<String> DEST = List.of("LHR", "JFK", "CDG", "SIN", "SYD", "BOM", "FRA", "HKG", "NRT", "JNB", "IST", "LAX", "DEL", "CAI");
    private static final int[] FARE = {2850, 4200, 2650, 2300, 4600, 950, 2500, 2700, 3600, 2900, 1650, 4900, 980, 1200};
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public SyntheticHistorySeeder(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Integer existing = jdbc.queryForObject("SELECT count(*) FROM rpt_bookings WHERE synthetic", Integer.class);
        if (existing != null && existing > 0) return;
        Random rnd = new Random(7);
        LocalDate today = LocalDate.now(clock);
        int rows = 0;
        for (int d = 90; d >= 1; d--) {
            LocalDate day = today.minusDays(d);
            int perDay = 28 + rnd.nextInt(18) + (day.getDayOfWeek().getValue() >= 5 ? 10 : 0);
            for (int i = 0; i < perDay; i++) {
                int r = rnd.nextInt(DEST.size());
                boolean outbound = rnd.nextBoolean();
                int pax = 1 + (rnd.nextInt(10) < 7 ? 0 : rnd.nextInt(3));
                boolean business = rnd.nextInt(10) == 0;
                BigDecimal amount = BigDecimal.valueOf((long) FARE[r] * pax * (business ? 3 : 1) + rnd.nextInt(300)).setScale(2);
                boolean cancelled = rnd.nextInt(100) < 6;
                Instant created = day.atTime(6 + rnd.nextInt(16), rnd.nextInt(60)).toInstant(ZoneOffset.UTC);
                jdbc.update("""
                        INSERT INTO rpt_bookings (booking_id, pnr, flight_id, flight_number, origin, destination, departure_time,
                          contact_email, status, passengers, amount, refund_amount, currency, channel, synthetic, created_at, cancelled_at)
                        VALUES (?,?,NULL,?,?,?,?,?,?,?,?,?,'AED',?,TRUE,?,?)
                        """, UUID.randomUUID(), "SY" + String.format("%04d", rows % 10000),
                        "AF" + (100 + r * 2 + (outbound ? 0 : 1)), outbound ? "DXB" : DEST.get(r), outbound ? DEST.get(r) : "DXB",
                        Timestamp.from(created.plus(Duration.ofDays(5 + rnd.nextInt(40)))),
                        "synthetic.customer" + rnd.nextInt(900) + "@example.com", cancelled ? "CANCELLED" : "CONFIRMED", pax,
                        amount, cancelled ? amount : BigDecimal.ZERO, rnd.nextInt(5) == 0 ? "AGENT" : "WEB",
                        Timestamp.from(created), cancelled ? Timestamp.from(created.plus(Duration.ofDays(2))) : null);
                rows++;
            }
        }
        log.info("Seeded {} SYNTHETIC historical bookings for demo reporting", rows);
    }
}
