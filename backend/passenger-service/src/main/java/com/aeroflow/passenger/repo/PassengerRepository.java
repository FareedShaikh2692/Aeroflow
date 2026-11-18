package com.aeroflow.passenger.repo;

import com.aeroflow.passenger.domain.Passenger;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PassengerRepository extends JpaRepository<Passenger, UUID>, JpaSpecificationExecutor<Passenger> {
    Optional<Passenger> findByEmailIgnoreCase(String email);
    Optional<Passenger> findByUserId(UUID userId);
    boolean existsByEmailIgnoreCase(String email);
}
