package com.aeroflow.flight.repo;

import com.aeroflow.flight.domain.Flight;
import com.aeroflow.flight.domain.FlightStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface FlightRepository extends JpaRepository<Flight, UUID>, JpaSpecificationExecutor<Flight> {

    @Query("""
           select f from Flight f
           where f.origin.code = :origin and f.destination.code = :destination
             and f.departureTime >= :from and f.departureTime < :to
             and f.status <> com.aeroflow.flight.domain.FlightStatus.CANCELLED
           order by f.departureTime
           """)
    List<Flight> search(String origin, String destination, Instant from, Instant to);

    boolean existsByFlightNumberAndDepartureTime(String flightNumber, Instant departureTime);

    long countByStatus(FlightStatus status);

    long countByDepartureTimeBetween(Instant from, Instant to);

    @Query("select f.status, count(f) from Flight f where f.departureTime >= :from and f.departureTime < :to group by f.status")
    List<Object[]> countByStatusBetween(Instant from, Instant to);
}
