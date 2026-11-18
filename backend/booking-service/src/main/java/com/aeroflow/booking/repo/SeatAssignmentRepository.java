package com.aeroflow.booking.repo;

import com.aeroflow.booking.domain.SeatAssignment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SeatAssignmentRepository extends JpaRepository<SeatAssignment, UUID> {
    @Query("select s.seatNumber from SeatAssignment s where s.flightId = :flightId and s.released = false")
    List<String> occupiedSeats(UUID flightId);

    Optional<SeatAssignment> findByPassengerIdAndReleasedFalse(UUID passengerId);
}
