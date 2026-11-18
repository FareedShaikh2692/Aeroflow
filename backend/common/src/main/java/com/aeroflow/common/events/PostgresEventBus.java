package com.aeroflow.common.events;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Kafka-free event transport on PostgreSQL (aeroflow.events.transport=postgres).
 *
 * <p>All services append to one shared, append-only log ({@code eventbus.events}) and each consumer group
 * tracks its own offset per topic ({@code eventbus.consumer_offsets}), giving Kafka-like semantics:
 * ordered per topic, at-least-once, independent consumer groups, replay by resetting an offset.
 * A session-level advisory lock per (group, topic) ensures one replica consumes a partition at a time.
 * Handlers stay idempotent through each service's {@link EventInbox}. Poison messages are retried and then
 * parked in {@code eventbus.dead_letters}.
 *
 * <p>The bus lives in the service's own database (schema {@code eventbus}) when every service shares one
 * PostgreSQL database, or in a dedicated database configured with {@code aeroflow.events.bus.url}.
 */
@Component
@ConditionalOnProperty(name = "aeroflow.events.transport", havingValue = "postgres")
public class PostgresEventBus implements EventTransport, DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(PostgresEventBus.class);
    private static final int BATCH = 100;
    private static final int MAX_ATTEMPTS = 5;

    private final DataSource bus;
    private final HikariDataSource owned;
    private final List<EventSubscriber> subscribers;
    private final String group;
    private final Map<Long, Integer> failures = new ConcurrentHashMap<>();

    public PostgresEventBus(DataSource primary, List<EventSubscriber> subscribers,
                            @Value("${spring.application.name}") String group,
                            @Value("${aeroflow.events.bus.url:}") String busUrl,
                            @Value("${aeroflow.events.bus.username:${spring.datasource.username:}}") String user,
                            @Value("${aeroflow.events.bus.password:${spring.datasource.password:}}") String password) {
        this.subscribers = subscribers;
        this.group = group;
        if (busUrl == null || busUrl.isBlank()) {
            this.owned = null;
            this.bus = primary;
        } else {
            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl(busUrl);
            ds.setUsername(user);
            ds.setPassword(password);
            ds.setMaximumPoolSize(3);
            ds.setPoolName("eventbus");
            this.owned = ds;
            this.bus = ds;
        }
        initSchema();
        log.info("PostgreSQL event bus active for group '{}' with {} subscriber(s)", group, subscribers.size());
    }

    private void initSchema() {
        try (Connection c = bus.getConnection(); Statement st = c.createStatement()) {
            c.setAutoCommit(false);
            st.execute("SELECT pg_advisory_xact_lock(727274)"); // serialise DDL across services starting together
            st.execute("CREATE SCHEMA IF NOT EXISTS eventbus");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS eventbus.events (
                      id BIGSERIAL PRIMARY KEY, topic VARCHAR(120) NOT NULL, event_key VARCHAR(120) NOT NULL,
                      payload TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now())""");
            st.execute("CREATE INDEX IF NOT EXISTS ix_eventbus_topic_id ON eventbus.events (topic, id)");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS eventbus.consumer_offsets (
                      consumer_group VARCHAR(80) NOT NULL, topic VARCHAR(120) NOT NULL, last_id BIGINT NOT NULL DEFAULT 0,
                      updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY (consumer_group, topic))""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS eventbus.dead_letters (
                      id BIGSERIAL PRIMARY KEY, consumer_group VARCHAR(80) NOT NULL, event_id BIGINT NOT NULL,
                      topic VARCHAR(120) NOT NULL, payload TEXT NOT NULL, error VARCHAR(1000),
                      created_at TIMESTAMPTZ NOT NULL DEFAULT now())""");
            c.commit();
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot initialise PostgreSQL event bus", e);
        }
    }

    @Override
    public void send(String topic, String key, String payload) throws SQLException {
        try (Connection c = bus.getConnection();
             PreparedStatement ps = c.prepareStatement("INSERT INTO eventbus.events (topic, event_key, payload) VALUES (?,?,?)")) {
            ps.setString(1, topic);
            ps.setString(2, key);
            ps.setString(3, payload);
            ps.executeUpdate();
        }
    }

    @Scheduled(fixedDelayString = "${aeroflow.events.poll-interval-ms:1000}", initialDelay = 3000)
    public void poll() {
        for (EventSubscriber s : subscribers) {
            for (String topic : s.topics()) {
                try {
                    consume(s, topic);
                } catch (SQLException e) {
                    log.warn("Event bus poll failed for {}/{}: {}", group, topic, e.getMessage());
                }
            }
        }
    }

    /** Consumes one batch for (group, topic). Returns the number of events handled. */
    int consume(EventSubscriber subscriber, String topic) throws SQLException {
        String group = consumerGroup(subscriber);
        long lockKey = ((long) group.hashCode() << 32) | (topic.hashCode() & 0xffffffffL);
        try (Connection c = bus.getConnection()) {
            c.setAutoCommit(true);
            if (!tryLock(c, lockKey)) return 0; // another replica owns this partition right now
            try {
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO eventbus.consumer_offsets (consumer_group, topic, last_id) VALUES (?, ?, 0)
                        ON CONFLICT DO NOTHING""")) {
                    ps.setString(1, group);
                    ps.setString(2, topic);
                    ps.executeUpdate();
                }
                long last;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT last_id FROM eventbus.consumer_offsets WHERE consumer_group = ? AND topic = ?")) {
                    ps.setString(1, group);
                    ps.setString(2, topic);
                    try (ResultSet rs = ps.executeQuery()) { rs.next(); last = rs.getLong(1); }
                }
                int handled = 0;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT id, payload FROM eventbus.events WHERE topic = ? AND id > ? ORDER BY id LIMIT " + BATCH)) {
                    ps.setString(1, topic);
                    ps.setLong(2, last);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            long id = rs.getLong(1);
                            String payload = rs.getString(2);
                            if (!dispatch(c, subscriber, group, topic, id, payload)) break; // keep order: retry next tick
                            commitOffset(c, group, topic, id);
                            handled++;
                        }
                    }
                }
                return handled;
            } finally {
                unlock(c, lockKey);
            }
        }
    }

    private boolean dispatch(Connection c, EventSubscriber s, String group, String topic, long id, String payload)
            throws SQLException {
        try {
            EventInbox.runAs(s.consumerName(), () -> s.handle(payload));
            failures.remove(id * 31 + group.hashCode());
            return true;
        } catch (RuntimeException e) {
            int attempts = failures.merge(id * 31 + group.hashCode(), 1, Integer::sum);
            if (attempts < MAX_ATTEMPTS) {
                log.warn("Handler failed for event {} on {} (attempt {}/{}): {}", id, topic, attempts, MAX_ATTEMPTS, e.getMessage());
                return false;
            }
            log.error("Event {} on {} moved to dead letters after {} attempts", id, topic, attempts, e);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO eventbus.dead_letters (consumer_group, event_id, topic, payload, error) VALUES (?,?,?,?,?)")) {
                ps.setString(1, group);
                ps.setLong(2, id);
                ps.setString(3, topic);
                ps.setString(4, payload);
                String msg = String.valueOf(e.getMessage());
                ps.setString(5, msg.substring(0, Math.min(1000, msg.length())));
                ps.executeUpdate();
            }
            failures.remove(id * 31 + group.hashCode());
            return true; // skip the poison message
        }
    }

    /** One consumer group per subscriber, so several subscribers can share a deployable. */
    String consumerGroup(EventSubscriber s) {
        return group + "/" + s.consumerName();
    }

    private void commitOffset(Connection c, String group, String topic, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE eventbus.consumer_offsets SET last_id = ?, updated_at = now() WHERE consumer_group = ? AND topic = ?")) {
            ps.setLong(1, id);
            ps.setString(2, group);
            ps.setString(3, topic);
            ps.executeUpdate();
        }
    }

    /** Keeps the shared log small: events older than the retention window are deleted (one service at a time). */
    @Scheduled(cron = "0 45 3 * * *")
    public void purge() {
        try (Connection c = bus.getConnection(); Statement st = c.createStatement()) {
            if (!tryLock(c, 727275L)) return;
            try {
                int n = st.executeUpdate("DELETE FROM eventbus.events WHERE created_at < now() - interval '7 days'");
                if (n > 0) log.info("Purged {} events older than 7 days from the event bus", n);
            } finally {
                unlock(c, 727275L);
            }
        } catch (SQLException e) {
            log.warn("Event bus purge failed: {}", e.getMessage());
        }
    }

    private static boolean tryLock(Connection c, long key) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            ps.setLong(1, key);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getBoolean(1); }
        }
    }

    private static void unlock(Connection c, long key) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT pg_advisory_unlock(?)")) {
            ps.setLong(1, key);
            ps.execute();
        }
    }

    @Override
    public void destroy() {
        if (owned != null) owned.close();
    }
}
