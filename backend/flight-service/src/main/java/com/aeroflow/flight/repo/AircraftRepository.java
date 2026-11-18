package com.aeroflow.flight.repo;

import com.aeroflow.flight.domain.Aircraft;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AircraftRepository extends JpaRepository<Aircraft, UUID> {
    boolean existsByRegistrationIgnoreCase(String registration);
}
