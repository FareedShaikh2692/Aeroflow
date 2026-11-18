package com.aeroflow.flight.api;

import com.aeroflow.common.security.Roles;
import com.aeroflow.common.web.PageResponse;
import com.aeroflow.flight.api.FlightDtos.*;
import com.aeroflow.flight.domain.FlightStatus;
import com.aeroflow.flight.service.FlightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@Tag(name = "Flights")
public class FlightController {
    private static final Set<String> SORTABLE = Set.of("departureTime", "arrivalTime", "flightNumber", "status", "updatedAt");
    private final FlightService flights;

    public FlightController(FlightService flights) {
        this.flights = flights;
    }

    @GetMapping("/api/flights/search")
    @Operation(summary = "Public flight search by route and local departure date")
    public List<FlightResponse> search(@RequestParam @Pattern(regexp = "^[A-Za-z]{3}$") String origin,
                                       @RequestParam @Pattern(regexp = "^[A-Za-z]{3}$") String destination,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return flights.search(origin, destination, date);
    }

    @GetMapping("/api/flights/public/{id}")
    @Operation(summary = "Public flight details (used by booking and check-in flows)")
    public FlightResponse publicGet(@PathVariable UUID id) {
        return flights.get(id);
    }

    @GetMapping("/api/flights")
    @PreAuthorize(Roles.IS_STAFF)
    public PageResponse<FlightResponse> list(@RequestParam(required = false) String q,
                                             @RequestParam(required = false) FlightStatus status,
                                             @RequestParam(required = false) String origin,
                                             @RequestParam(required = false) String destination,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             @RequestParam(defaultValue = "departureTime") String sort,
                                             @RequestParam(defaultValue = "asc") String dir) {
        String sortField = SORTABLE.contains(sort) ? sort : "departureTime";
        Sort s = Sort.by("desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC, sortField);
        return flights.list(q, status, origin, destination, from, to,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(size, 1), 100), s));
    }

    @GetMapping("/api/flights/stats")
    @PreAuthorize(Roles.IS_STAFF)
    public FlightStats stats() {
        return flights.stats();
    }

    @GetMapping("/api/flights/{id}")
    @PreAuthorize(Roles.IS_STAFF)
    public FlightResponse get(@PathVariable UUID id) {
        return flights.get(id);
    }

    @PostMapping("/api/flights")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.IS_OPS)
    public FlightResponse create(@Valid @RequestBody FlightRequest req) {
        return flights.create(req);
    }

    @PutMapping("/api/flights/{id}")
    @PreAuthorize(Roles.IS_OPS)
    @Operation(summary = "Update a flight; pass If-Match with the version for optimistic concurrency")
    public FlightResponse update(@PathVariable UUID id, @Valid @RequestBody FlightRequest req,
                                 @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        Long version = ifMatch == null ? null : Long.parseLong(ifMatch.replace("\"", "").trim());
        return flights.update(id, req, version);
    }

    @PatchMapping("/api/flights/{id}/status")
    @PreAuthorize(Roles.IS_OPS)
    @Operation(summary = "Change operational status: delay, board, depart, arrive or cancel")
    public FlightResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody StatusChangeRequest req) {
        return flights.changeStatus(id, req);
    }

    @DeleteMapping("/api/flights/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.IS_ADMIN)
    public void delete(@PathVariable UUID id) {
        flights.delete(id);
    }

    @GetMapping("/internal/flights/{id}")
    public FlightResponse internalGet(@PathVariable UUID id) {
        return flights.get(id);
    }
}
