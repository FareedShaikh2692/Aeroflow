package com.aeroflow.booking.repo;

import com.aeroflow.booking.domain.FlightInventory;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;

public interface FlightInventoryRepository extends JpaRepository<FlightInventory, UUID> {

    /** Serialises all seat-changing transactions for one flight. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select i from FlightInventory i where i.flightId = :flightId")
    Optional<FlightInventory> lockById(UUID flightId);

    @Modifying
    @Query(value = """
            INSERT INTO flight_inventory (flight_id, flight_number, capacity, booked_seats, status, updated_at)
            VALUES (:flightId, :flightNumber, :capacity, 0, :status, now())
            ON CONFLICT (flight_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(UUID flightId, String flightNumber, int capacity, String status);
}
