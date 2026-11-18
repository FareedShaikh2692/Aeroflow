package com.aeroflow.passenger.repo;

import com.aeroflow.passenger.domain.TravelRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TravelRecordRepository extends JpaRepository<TravelRecord, UUID> {
    List<TravelRecord> findByEmailIgnoreCaseOrderByDepartureTimeDesc(String email);
    Optional<TravelRecord> findByBookingIdAndEmailIgnoreCase(UUID bookingId, String email);
    List<TravelRecord> findByBookingId(UUID bookingId);
}
