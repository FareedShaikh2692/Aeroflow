package com.aeroflow.flight.repo;

import com.aeroflow.flight.domain.Airport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AirportRepository extends JpaRepository<Airport, String> {}
