package com.aeroflow.passenger.api;

import com.aeroflow.common.security.Roles;
import com.aeroflow.common.web.PageResponse;
import com.aeroflow.passenger.api.PassengerDtos.*;
import com.aeroflow.passenger.service.PassengerService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/passengers")
@Tag(name = "Passengers")
public class PassengerController {
    private static final Set<String> SORTABLE = Set.of("createdAt", "lastName", "email", "loyaltyPoints");
    private final PassengerService service;

    public PassengerController(PassengerService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(Roles.IS_STAFF)
    public PageResponse<PassengerResponse> list(@RequestParam(required = false) String q,
                                                @RequestParam(required = false) String tier,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size,
                                                @RequestParam(defaultValue = "createdAt") String sort,
                                                @RequestParam(defaultValue = "desc") String dir) {
        Sort s = Sort.by("asc".equalsIgnoreCase(dir) ? Sort.Direction.ASC : Sort.Direction.DESC,
                SORTABLE.contains(sort) ? sort : "createdAt");
        return service.search(q, tier, PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100), s));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('PASSENGER')")
    public PassengerDetail me() {
        return service.me();
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('PASSENGER')")
    public PassengerResponse updateMe(@Valid @RequestBody PassengerRequest req) {
        return service.updateMe(req);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Roles.IS_STAFF)
    public PassengerDetail get(@PathVariable UUID id) {
        return service.detail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.IS_FLIGHT_STAFF)
    public PassengerResponse create(@Valid @RequestBody PassengerRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Roles.IS_FLIGHT_STAFF)
    public PassengerResponse update(@PathVariable UUID id, @Valid @RequestBody PassengerRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.IS_ADMIN)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
