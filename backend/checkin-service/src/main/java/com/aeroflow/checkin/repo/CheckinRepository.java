package com.aeroflow.checkin.repo;

import com.aeroflow.checkin.domain.Checkin;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CheckinRepository extends JpaRepository<Checkin, UUID> {
    List<Checkin> findByPnrOrderByLastName(String pnr);
    Optional<Checkin> findByPassengerId(UUID passengerId);
    Optional<Checkin> findByBoardingPassNumber(String number);
    Page<Checkin> findByFlightId(UUID flightId, Pageable pageable);
    Page<Checkin> findAllBy(Pageable pageable);

    @Query(value = """
            INSERT INTO flight_sequences (flight_id, last_seq) VALUES (:flightId, 1)
            ON CONFLICT (flight_id) DO UPDATE SET last_seq = flight_sequences.last_seq + 1
            RETURNING last_seq
            """, nativeQuery = true)
    int nextSequence(UUID flightId);
}
