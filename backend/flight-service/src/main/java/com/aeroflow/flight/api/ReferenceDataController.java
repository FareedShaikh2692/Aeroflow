package com.aeroflow.flight.api;

import com.aeroflow.common.security.Roles;
import com.aeroflow.flight.api.FlightDtos.*;
import com.aeroflow.flight.service.ReferenceDataService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Reference data")
public class ReferenceDataController {
    private final ReferenceDataService ref;

    public ReferenceDataController(ReferenceDataService ref) {
        this.ref = ref;
    }

    @GetMapping("/api/airports")
    public List<AirportResponse> airports() {
        return ref.airports();
    }

    @GetMapping("/api/aircraft")
    @PreAuthorize(Roles.IS_STAFF)
    public List<AircraftResponse> fleet() {
        return ref.fleet();
    }

    @PostMapping("/api/aircraft")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.IS_OPS)
    public AircraftResponse create(@Valid @RequestBody AircraftRequest req) {
        return ref.createAircraft(req);
    }

    @PatchMapping("/api/aircraft/{id}/status")
    @PreAuthorize(Roles.IS_OPS)
    public AircraftResponse status(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        return ref.setAircraftStatus(id, body.getOrDefault("status", ""));
    }
}
