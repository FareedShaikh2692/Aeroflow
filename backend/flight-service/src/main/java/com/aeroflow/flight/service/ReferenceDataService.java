package com.aeroflow.flight.service;

import com.aeroflow.common.audit.AuditService;
import com.aeroflow.common.error.ApiException;
import com.aeroflow.flight.api.FlightDtos.*;
import com.aeroflow.flight.domain.Aircraft;
import com.aeroflow.flight.repo.AircraftRepository;
import com.aeroflow.flight.repo.AirportRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReferenceDataService {
    private final AirportRepository airports;
    private final AircraftRepository aircraft;
    private final AuditService audit;

    public ReferenceDataService(AirportRepository airports, AircraftRepository aircraft, AuditService audit) {
        this.airports = airports;
        this.aircraft = aircraft;
        this.audit = audit;
    }

    @Cacheable("airports")
    @Transactional(readOnly = true)
    public List<AirportResponse> airports() {
        return airports.findAll(Sort.by("city")).stream().map(FlightMapper::toResponse).toList();
    }

    @Cacheable("aircraft")
    @Transactional(readOnly = true)
    public List<AircraftResponse> fleet() {
        return aircraft.findAll(Sort.by("model", "registration")).stream().map(FlightMapper::toResponse).toList();
    }

    @CacheEvict(value = "aircraft", allEntries = true)
    @Transactional
    public AircraftResponse createAircraft(AircraftRequest req) {
        if (aircraft.existsByRegistrationIgnoreCase(req.registration())) {
            throw ApiException.conflict("DUPLICATE_REGISTRATION", "Aircraft " + req.registration() + " already exists");
        }
        Aircraft a = aircraft.save(new Aircraft(req.registration().toUpperCase(), req.model(), req.manufacturer(),
                req.businessRows(), req.businessLayout(), req.economyRows(), req.economyLayout()));
        audit.record("AIRCRAFT_CREATED", "Aircraft", a.getId().toString(), a.getRegistration() + " " + a.getModel());
        return FlightMapper.toResponse(a);
    }

    @CacheEvict(value = "aircraft", allEntries = true)
    @Transactional
    public AircraftResponse setAircraftStatus(UUID id, String status) {
        if (!List.of("ACTIVE", "MAINTENANCE", "RETIRED").contains(status)) {
            throw ApiException.badRequest("INVALID_STATUS", "Status must be ACTIVE, MAINTENANCE or RETIRED");
        }
        Aircraft a = aircraft.findById(id).orElseThrow(() -> ApiException.notFound("Aircraft", id));
        a.setStatus(status);
        audit.record("AIRCRAFT_STATUS_CHANGED", "Aircraft", id.toString(), a.getRegistration() + " -> " + status);
        return FlightMapper.toResponse(a);
    }
}
