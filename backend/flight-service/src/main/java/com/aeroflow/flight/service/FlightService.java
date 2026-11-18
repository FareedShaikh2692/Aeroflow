package com.aeroflow.flight.service;

import com.aeroflow.common.audit.AuditService;
import com.aeroflow.common.error.ApiException;
import com.aeroflow.common.events.DomainEventPublisher;
import com.aeroflow.common.events.EventTypes;
import com.aeroflow.common.events.Topics;
import com.aeroflow.common.web.PageResponse;
import com.aeroflow.flight.api.FlightDtos.*;
import com.aeroflow.flight.domain.*;
import com.aeroflow.flight.repo.AircraftRepository;
import com.aeroflow.flight.repo.AirportRepository;
import com.aeroflow.flight.repo.FlightRepository;
import java.time.*;
import java.util.*;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FlightService {
    private final FlightRepository flights;
    private final AirportRepository airports;
    private final AircraftRepository aircraft;
    private final DomainEventPublisher events;
    private final AuditService audit;
    private final BookingClient bookings;
    private final Clock clock;

    public FlightService(FlightRepository flights, AirportRepository airports, AircraftRepository aircraft,
                         DomainEventPublisher events, AuditService audit, BookingClient bookings, Clock clock) {
        this.flights = flights;
        this.airports = airports;
        this.aircraft = aircraft;
        this.events = events;
        this.audit = audit;
        this.bookings = bookings;
        this.clock = clock;
    }

    /** Searches by calendar date in the origin airport's local timezone. */
    @Cacheable(value = "flightSearch", key = "#origin + ':' + #destination + ':' + #date")
    @Transactional(readOnly = true)
    public List<FlightResponse> search(String origin, String destination, LocalDate date) {
        Airport o = airport(origin.toUpperCase());
        airport(destination.toUpperCase());
        ZoneId zone = ZoneId.of(o.getTimezone());
        Instant from = date.atStartOfDay(zone).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(zone).toInstant();
        Instant notBefore = Instant.now(clock).plus(Duration.ofMinutes(45));
        return flights.search(o.getCode(), destination.toUpperCase(), from, to).stream()
                .filter(f -> f.getDepartureTime().isAfter(notBefore))
                .filter(f -> f.getStatus() == FlightStatus.SCHEDULED || f.getStatus() == FlightStatus.DELAYED)
                .map(FlightMapper::toResponse).toList();
    }

    @Cacheable(value = "flight", key = "#id")
    @Transactional(readOnly = true)
    public FlightResponse get(UUID id) {
        return FlightMapper.toResponse(find(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<FlightResponse> list(String q, FlightStatus status, String origin, String destination,
                                             LocalDate from, LocalDate to, Pageable pageable) {
        Specification<Flight> spec = (r, cq, cb) -> cb.conjunction();
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toUpperCase() + "%";
            spec = spec.and((r, cq, cb) -> cb.or(cb.like(r.get("flightNumber"), like),
                    cb.like(cb.upper(r.get("origin").get("city")), like),
                    cb.like(cb.upper(r.get("destination").get("city")), like)));
        }
        if (status != null) spec = spec.and((r, cq, cb) -> cb.equal(r.get("status"), status));
        if (origin != null && !origin.isBlank()) spec = spec.and((r, cq, cb) -> cb.equal(r.get("origin").get("code"), origin.toUpperCase()));
        if (destination != null && !destination.isBlank()) spec = spec.and((r, cq, cb) -> cb.equal(r.get("destination").get("code"), destination.toUpperCase()));
        if (from != null) {
            Instant f = from.atStartOfDay(ZoneOffset.UTC).toInstant();
            spec = spec.and((r, cq, cb) -> cb.greaterThanOrEqualTo(r.get("departureTime"), f));
        }
        if (to != null) {
            Instant t = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            spec = spec.and((r, cq, cb) -> cb.lessThan(r.get("departureTime"), t));
        }
        return PageResponse.of(flights.findAll(spec, pageable), FlightMapper::toResponse);
    }

    @Caching(evict = {@CacheEvict(value = "flightSearch", allEntries = true)})
    @Transactional
    public FlightResponse create(FlightRequest req) {
        validate(req);
        if (flights.existsByFlightNumberAndDepartureTime(req.flightNumber().toUpperCase(), req.departureTime())) {
            throw ApiException.conflict("DUPLICATE_FLIGHT", "Flight " + req.flightNumber() + " already exists at that time");
        }
        Flight f = new Flight(req.flightNumber(), airport(req.originCode()), airport(req.destinationCode()),
                activeAircraft(req.aircraftId()), req.departureTime(), req.arrivalTime(), req.economyFare(),
                req.businessFare(), "AED");
        f.setGate(req.gate());
        f.setTerminal(req.terminal());
        flights.save(f);
        FlightResponse res = FlightMapper.toResponse(f);
        events.publish(Topics.FLIGHT, EventTypes.FLIGHT_CREATED, f.getId().toString(), FlightEventPayload.of(f));
        audit.record("FLIGHT_CREATED", "Flight", f.getId().toString(), f.getFlightNumber() + " " +
                f.getOrigin().getCode() + "-" + f.getDestination().getCode() + " " + f.getDepartureTime());
        return res;
    }

    @Caching(evict = {@CacheEvict(value = "flight", key = "#id"), @CacheEvict(value = "flightSearch", allEntries = true),
            @CacheEvict(value = "flightLayout", key = "#id")})
    @Transactional
    public FlightResponse update(UUID id, FlightRequest req, Long expectedVersion) {
        validate(req);
        Flight f = find(id);
        checkVersion(f, expectedVersion);
        if (f.getStatus() == FlightStatus.CANCELLED || f.getStatus() == FlightStatus.ARRIVED
                || f.getStatus() == FlightStatus.DEPARTED) {
            throw ApiException.conflict("FLIGHT_LOCKED", "A " + f.getStatus() + " flight cannot be edited");
        }
        Aircraft ac = activeAircraft(req.aircraftId());
        if (!ac.getId().equals(f.getAircraft().getId()) && ac.capacity() < f.getAircraft().capacity()
                && bookings.activeBookingCount(id) > 0) {
            throw ApiException.conflict("AIRCRAFT_DOWNGRADE", "Cannot swap to a smaller aircraft while bookings exist");
        }
        f.update(req.flightNumber(), airport(req.originCode()), airport(req.destinationCode()), ac,
                req.departureTime(), req.arrivalTime(), req.economyFare(), req.businessFare());
        f.setGate(req.gate());
        f.setTerminal(req.terminal());
        flights.flush();
        events.publish(Topics.FLIGHT, EventTypes.FLIGHT_UPDATED, id.toString(), FlightEventPayload.of(f));
        audit.record("FLIGHT_UPDATED", "Flight", id.toString(), f.getFlightNumber());
        return FlightMapper.toResponse(f);
    }

    @Caching(evict = {@CacheEvict(value = "flight", key = "#id"), @CacheEvict(value = "flightSearch", allEntries = true)})
    @Transactional
    public FlightResponse changeStatus(UUID id, StatusChangeRequest req) {
        Flight f = find(id);
        FlightStatus previous = f.getStatus();
        if (req.status() == FlightStatus.DELAYED && (req.delayMinutes() == null || req.delayMinutes() < 1)) {
            throw ApiException.badRequest("DELAY_REQUIRED", "delayMinutes is required when delaying a flight");
        }
        if (req.status() == FlightStatus.CANCELLED && (req.reason() == null || req.reason().isBlank())) {
            throw ApiException.badRequest("REASON_REQUIRED", "A reason is required when cancelling a flight");
        }
        try {
            f.changeStatus(req.status(), req.delayMinutes(), req.reason());
        } catch (IllegalStateException e) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", e.getMessage());
        }
        if (req.gate() != null && !req.gate().isBlank()) f.setGate(req.gate());
        flights.flush();
        Map<String, Object> payload = new LinkedHashMap<>(FlightEventPayload.of(f));
        payload.put("previousStatus", previous.name());
        events.publish(Topics.FLIGHT, EventTypes.FLIGHT_STATUS_CHANGED, id.toString(), payload);
        audit.record("FLIGHT_STATUS_CHANGED", "Flight", id.toString(), f.getFlightNumber() + " " + previous + " -> "
                + f.getStatus() + (req.reason() == null ? "" : " (" + req.reason() + ")"));
        return FlightMapper.toResponse(f);
    }

    @Caching(evict = {@CacheEvict(value = "flight", key = "#id"), @CacheEvict(value = "flightSearch", allEntries = true),
            @CacheEvict(value = "flightLayout", key = "#id")})
    @Transactional
    public void delete(UUID id) {
        Flight f = find(id);
        long active = bookings.activeBookingCount(id);
        if (active > 0) {
            throw ApiException.conflict("FLIGHT_HAS_BOOKINGS",
                    "Flight has " + active + " active booking(s); cancel the flight instead of deleting it");
        }
        flights.delete(f);
        events.publish(Topics.FLIGHT, EventTypes.FLIGHT_DELETED, id.toString(), Map.of("flightId", id,
                "flightNumber", f.getFlightNumber()));
        audit.record("FLIGHT_DELETED", "Flight", id.toString(), f.getFlightNumber() + " " + f.getDepartureTime());
    }

    @Transactional(readOnly = true)
    public FlightStats stats() {
        Instant now = Instant.now(clock);
        Instant dayStart = now.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        Map<FlightStatus, Long> byStatus = new EnumMap<>(FlightStatus.class);
        for (FlightStatus s : FlightStatus.values()) byStatus.put(s, flights.countByStatus(s));
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        return new FlightStats(total, byStatus.get(FlightStatus.SCHEDULED), byStatus.get(FlightStatus.DELAYED),
                byStatus.get(FlightStatus.CANCELLED), byStatus.get(FlightStatus.BOARDING),
                byStatus.get(FlightStatus.DEPARTED), byStatus.get(FlightStatus.ARRIVED),
                byStatus.get(FlightStatus.BOARDING) + byStatus.get(FlightStatus.DEPARTED),
                flights.countByDepartureTimeBetween(dayStart, dayStart.plus(Duration.ofDays(1))));
    }

    private void validate(FlightRequest req) {
        if (req.originCode().equalsIgnoreCase(req.destinationCode())) {
            throw ApiException.badRequest("INVALID_ROUTE", "Origin and destination must differ");
        }
        if (!req.arrivalTime().isAfter(req.departureTime())) {
            throw ApiException.badRequest("INVALID_TIMES", "Arrival must be after departure");
        }
        if (Duration.between(req.departureTime(), req.arrivalTime()).toHours() > 20) {
            throw ApiException.badRequest("INVALID_TIMES", "Flight duration cannot exceed 20 hours");
        }
        if (req.businessFare().compareTo(req.economyFare()) < 0) {
            throw ApiException.badRequest("INVALID_FARE", "Business fare must be at least the economy fare");
        }
    }

    private static void checkVersion(Flight f, Long expected) {
        if (expected != null && expected != f.getVersion()) {
            throw ApiException.conflict("STALE_VERSION", "This flight was changed by someone else. Reload and retry.");
        }
    }

    private Flight find(UUID id) {
        return flights.findById(id).orElseThrow(() -> ApiException.notFound("Flight", id));
    }

    private Airport airport(String code) {
        return airports.findById(code.toUpperCase()).orElseThrow(() -> ApiException.badRequest("UNKNOWN_AIRPORT",
                "Unknown airport code " + code));
    }

    private Aircraft activeAircraft(UUID id) {
        Aircraft a = aircraft.findById(id).orElseThrow(() -> ApiException.badRequest("UNKNOWN_AIRCRAFT", "Unknown aircraft"));
        if (!"ACTIVE".equals(a.getStatus())) throw ApiException.badRequest("AIRCRAFT_UNAVAILABLE", "Aircraft is not active");
        return a;
    }
}
