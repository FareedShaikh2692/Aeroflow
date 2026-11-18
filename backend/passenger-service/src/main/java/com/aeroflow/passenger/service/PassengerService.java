package com.aeroflow.passenger.service;

import com.aeroflow.common.audit.AuditService;
import com.aeroflow.common.error.ApiException;
import com.aeroflow.common.security.AuthenticatedUser;
import com.aeroflow.common.security.CurrentUser;
import com.aeroflow.common.web.PageResponse;
import com.aeroflow.passenger.api.PassengerDtos.*;
import com.aeroflow.passenger.domain.Passenger;
import com.aeroflow.passenger.domain.TravelRecord;
import com.aeroflow.passenger.repo.PassengerRepository;
import com.aeroflow.passenger.repo.TravelRecordRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PassengerService {
    private final PassengerRepository passengers;
    private final TravelRecordRepository history;
    private final AuditService audit;

    public PassengerService(PassengerRepository passengers, TravelRecordRepository history, AuditService audit) {
        this.passengers = passengers;
        this.history = history;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<PassengerResponse> search(String q, String tier, Pageable pageable) {
        Specification<Passenger> spec = (r, cq, cb) -> cb.conjunction();
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase() + "%";
            spec = spec.and((r, cq, cb) -> cb.or(cb.like(cb.lower(r.get("email")), like),
                    cb.like(cb.lower(cb.concat(cb.concat(r.get("firstName"), " "), r.get("lastName"))), like),
                    cb.like(cb.lower(r.get("passportNumber")), like), cb.like(r.get("phone"), like)));
        }
        if (tier != null && !tier.isBlank()) spec = spec.and((r, cq, cb) -> cb.equal(r.get("loyaltyTier"), tier));
        return PageResponse.of(passengers.findAll(spec, pageable), PassengerService::toResponse);
    }

    @Transactional(readOnly = true)
    public PassengerDetail detail(UUID id) {
        return detail(find(id));
    }

    @Transactional(readOnly = true)
    public PassengerDetail me() {
        AuthenticatedUser u = CurrentUser.require();
        Passenger p = passengers.findByUserId(u.id()).or(() -> passengers.findByEmailIgnoreCase(u.email()))
                .orElseThrow(() -> ApiException.notFound("Passenger profile", u.email()));
        return detail(p);
    }

    private PassengerDetail detail(Passenger p) {
        List<TravelRecordResponse> trips = history.findByEmailIgnoreCaseOrderByDepartureTimeDesc(p.getEmail()).stream()
                .map(PassengerService::toResponse).toList();
        Instant now = Instant.now();
        long upcoming = trips.stream().filter(t -> "CONFIRMED".equals(t.status()) && t.departureTime().isAfter(now)).count();
        BigDecimal spend = trips.stream().filter(t -> "CONFIRMED".equals(t.status()) && t.amount() != null)
                .map(TravelRecordResponse::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        long total = trips.stream().filter(t -> "CONFIRMED".equals(t.status())).count();
        return new PassengerDetail(toResponse(p), trips, total, upcoming, spend);
    }

    @Transactional
    public PassengerResponse create(PassengerRequest req) {
        if (passengers.existsByEmailIgnoreCase(req.email().trim())) {
            throw ApiException.conflict("EMAIL_IN_USE", "A passenger with this e-mail already exists");
        }
        Passenger p = new Passenger(null, req.firstName().trim(), req.lastName().trim(), req.email(), req.phone());
        apply(p, req);
        passengers.save(p);
        audit.record("PASSENGER_CREATED", "Passenger", p.getId().toString(), p.getEmail());
        return toResponse(p);
    }

    @Transactional
    public PassengerResponse update(UUID id, PassengerRequest req) {
        Passenger p = find(id);
        if (!p.getEmail().equalsIgnoreCase(req.email().trim()) && passengers.existsByEmailIgnoreCase(req.email().trim())) {
            throw ApiException.conflict("EMAIL_IN_USE", "A passenger with this e-mail already exists");
        }
        p.setFirstName(req.firstName().trim());
        p.setLastName(req.lastName().trim());
        p.setEmail(req.email());
        p.setPhone(req.phone());
        apply(p, req);
        audit.record("PASSENGER_UPDATED", "Passenger", id.toString(), p.getEmail());
        return toResponse(p);
    }

    @Transactional
    public PassengerResponse updateMe(PassengerRequest req) {
        AuthenticatedUser u = CurrentUser.require();
        Passenger p = passengers.findByUserId(u.id()).orElseThrow(() -> ApiException.notFound("Passenger profile", u.email()));
        p.setFirstName(req.firstName().trim());
        p.setLastName(req.lastName().trim());
        p.setPhone(req.phone());
        apply(p, req);
        p.setNotes(p.getNotes()); // passengers cannot edit staff notes
        return toResponse(p);
    }

    @Transactional
    public void delete(UUID id) {
        Passenger p = find(id);
        passengers.delete(p);
        audit.record("PASSENGER_DELETED", "Passenger", id.toString(), p.getEmail());
    }

    /** Called from user.registered events. Idempotent on e-mail. */
    @Transactional
    public void upsertFromRegistration(UUID userId, String email, String first, String last, String phone) {
        Passenger p = passengers.findByEmailIgnoreCase(email).orElse(null);
        if (p == null) {
            passengers.save(new Passenger(userId, first, last, email, phone == null || phone.isBlank() ? null : phone));
        } else if (p.getUserId() == null) {
            p.setUserId(userId);
        }
    }

    private static void apply(Passenger p, PassengerRequest req) {
        p.setDateOfBirth(req.dateOfBirth());
        p.setNationality(req.nationality());
        p.setPassportNumber(req.passportNumber());
        p.setPassportExpiry(req.passportExpiry());
        p.setAddressLine(req.addressLine());
        p.setCity(req.city());
        p.setCountry(req.country());
        if (CurrentUser.get().map(AuthenticatedUser::isStaff).orElse(false)) p.setNotes(req.notes());
    }

    private Passenger find(UUID id) {
        return passengers.findById(id).orElseThrow(() -> ApiException.notFound("Passenger", id));
    }

    static PassengerResponse toResponse(Passenger p) {
        return new PassengerResponse(p.getId(), p.getUserId(), p.getFirstName(), p.getLastName(), p.getEmail(), p.getPhone(),
                p.getDateOfBirth(), p.getNationality(), p.getPassportNumber(), p.getPassportExpiry(), p.getAddressLine(),
                p.getCity(), p.getCountry(), p.getLoyaltyTier(), p.getLoyaltyPoints(), p.getNotes(), p.getCreatedAt(),
                p.getUpdatedAt(), p.getVersion());
    }

    static TravelRecordResponse toResponse(TravelRecord t) {
        return new TravelRecordResponse(t.getBookingId(), t.getPnr(), t.getFlightNumber(), t.getOrigin(), t.getDestination(),
                t.getDepartureTime(), t.getStatus(), t.getAmount(), t.getCurrency(), t.isCheckedIn());
    }
}
