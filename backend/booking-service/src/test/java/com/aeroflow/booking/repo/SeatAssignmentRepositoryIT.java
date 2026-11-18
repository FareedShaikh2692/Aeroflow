package com.aeroflow.booking.repo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aeroflow.booking.domain.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Runs the real Flyway schema on PostgreSQL. Skipped automatically when Docker is unavailable (runs in CI). */
@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SeatAssignmentRepositoryIT {
    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", PG::getJdbcUrl);
        r.add("spring.datasource.username", PG::getUsername);
        r.add("spring.datasource.password", PG::getPassword);
        r.add("aeroflow.security.jwt.secret", () -> "test-secret-test-secret-test-secret-0123456789");
    }

    @Autowired BookingRepository bookings;
    @Autowired SeatAssignmentRepository seats;
    @Autowired FlightInventoryRepository inventory;

    private BookingPassenger newPassenger(UUID flightId) {
        Booking b = new Booking(UUID.randomUUID().toString().substring(0, 6).toUpperCase(),
                new Booking.FlightSnapshot(flightId, "AF100", "DXB", "LHR", Instant.now(), Instant.now().plusSeconds(3600)),
                null, "a@b.co", null, "AED", "WEB", "test");
        b.addPassenger(new BookingPassenger("Sarah", "Ahmed", null, null, null, null, Cabin.ECONOMY, "10A", BigDecimal.TEN));
        return bookings.saveAndFlush(b).getPassengers().get(0);
    }

    @Test
    void partialUniqueIndexPreventsDoubleBookingButAllowsRebookAfterRelease() {
        UUID flight = UUID.randomUUID();
        SeatAssignment first = seats.saveAndFlush(new SeatAssignment(flight, "10A", newPassenger(flight).getId()));
        UUID other = newPassenger(flight).getId();
        assertThatThrownBy(() -> seats.saveAndFlush(new SeatAssignment(flight, "10A", other)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void releasedSeatCanBeReassigned() {
        UUID flight = UUID.randomUUID();
        SeatAssignment first = seats.saveAndFlush(new SeatAssignment(flight, "11B", newPassenger(flight).getId()));
        first.release();
        seats.saveAndFlush(first);
        seats.saveAndFlush(new SeatAssignment(flight, "11B", newPassenger(flight).getId()));
        assertThat(seats.occupiedSeats(flight)).containsExactly("11B");
    }

    @Test
    void inventoryUpsertIsIdempotentAndCapacityConstraintHolds() {
        UUID flight = UUID.randomUUID();
        inventory.insertIfAbsent(flight, "AF100", 2, "SCHEDULED");
        inventory.insertIfAbsent(flight, "AF100", 999, "SCHEDULED");
        FlightInventory inv = inventory.lockById(flight).orElseThrow();
        assertThat(inv.getCapacity()).isEqualTo(2);
        assertThatThrownBy(() -> inv.reserve(3)).isInstanceOf(IllegalStateException.class);
    }
}
