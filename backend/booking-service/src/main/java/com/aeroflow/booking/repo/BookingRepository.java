package com.aeroflow.booking.repo;

import com.aeroflow.booking.domain.Booking;
import com.aeroflow.booking.domain.BookingStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface BookingRepository extends JpaRepository<Booking, UUID>, JpaSpecificationExecutor<Booking> {
    Optional<Booking> findByPnr(String pnr);
    boolean existsByPnr(String pnr);
    List<Booking> findByUserIdOrderByDepartureTimeDesc(UUID userId);
    List<Booking> findByContactEmailIgnoreCaseOrderByDepartureTimeDesc(String email);
    List<Booking> findByFlightIdAndStatus(UUID flightId, BookingStatus status);
    long countByFlightIdAndStatus(UUID flightId, BookingStatus status);

    @Query("""
           select b from Booking b where b.status = com.aeroflow.booking.domain.BookingStatus.CONFIRMED
             and b.reminderSent = false and b.departureTime > :now and b.departureTime <= :until
           order by b.departureTime
           """)
    List<Booking> findDueForReminder(Instant now, Instant until, Pageable pageable);
}
