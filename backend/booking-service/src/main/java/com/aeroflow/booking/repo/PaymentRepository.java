package com.aeroflow.booking.repo;

import com.aeroflow.booking.domain.Payment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByBookingIdOrderByCreatedAt(UUID bookingId);
}
