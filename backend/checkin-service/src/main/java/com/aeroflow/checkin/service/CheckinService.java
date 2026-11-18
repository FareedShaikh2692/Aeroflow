package com.aeroflow.checkin.service;

import com.aeroflow.checkin.api.CheckinDtos.*;
import com.aeroflow.checkin.domain.Checkin;
import com.aeroflow.checkin.repo.CheckinRepository;
import com.aeroflow.common.audit.AuditService;
import com.aeroflow.common.error.ApiException;
import com.aeroflow.common.events.DomainEventPublisher;
import com.aeroflow.common.events.EventTypes;
import com.aeroflow.common.events.Topics;
import com.aeroflow.common.security.AuthenticatedUser;
import com.aeroflow.common.security.CurrentUser;
import com.aeroflow.common.web.PageResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Online / agent check-in. Booking-service stays the source of truth for seats and the checked-in flag;
 * this service owns boarding passes. Completing is idempotent: re-running it re-issues missing passes only.
 */
@Service
public class CheckinService {
    private final BookingClient bookings;
    private final CheckinRepository checkins;
    private final DomainEventPublisher events;
    private final AuditService audit;
    private final Clock clock;
    private final Duration opensBefore;
    private final Duration closesBefore;

    public CheckinService(BookingClient bookings, CheckinRepository checkins, DomainEventPublisher events, AuditService audit,
                          Clock clock, @Value("${aeroflow.checkin.opens-hours-before:48}") int opensHours,
                          @Value("${aeroflow.checkin.closes-minutes-before:60}") int closesMinutes) {
        this.bookings = bookings;
        this.checkins = checkins;
        this.events = events;
        this.audit = audit;
        this.clock = clock;
        this.opensBefore = Duration.ofHours(opensHours);
        this.closesBefore = Duration.ofMinutes(closesMinutes);
    }

    @Transactional(readOnly = true)
    public CheckinView retrieve(String pnr, String lastName) {
        BookingClient.Booking b = bookings.retrieve(pnr.toUpperCase(), lastName);
        return new CheckinView(b, eligibility(b), passes(b.pnr()));
    }

    public Eligibility eligibility(BookingClient.Booking b) {
        Instant now = Instant.now(clock);
        Instant opens = b.departureTime().minus(opensBefore);
        Instant closes = b.departureTime().minus(closesBefore);
        if (!"CONFIRMED".equals(b.status())) return new Eligibility(false, opens, closes, "Booking is " + b.status());
        if (now.isBefore(opens)) return new Eligibility(false, opens, closes, "Online check-in opens " + opensBefore.toHours() + " hours before departure");
        if (now.isAfter(closes)) return new Eligibility(false, opens, closes, "Online check-in has closed for this flight");
        return new Eligibility(true, opens, closes, null);
    }

    public BookingClient.Booking changeSeat(SeatRequest req) {
        BookingClient.Booking b = bookings.retrieve(req.pnr().toUpperCase(), req.lastName());
        requireOpen(b);
        return bookings.changeSeat(b.pnr(), req.passengerId(), req.seatNumber());
    }

    @Transactional
    public CheckinView complete(CompleteRequest req) {
        BookingClient.Booking b = bookings.retrieve(req.pnr().toUpperCase(), req.lastName());
        AuthenticatedUser actor = CurrentUser.get().orElse(null);
        boolean agent = actor != null && actor.isStaff();
        if (!agent) requireOpen(b);
        else if (!"CONFIRMED".equals(b.status())) throw ApiException.conflict("BOOKING_NOT_CONFIRMED", "Booking is " + b.status());

        Map<UUID, BookingClient.Passenger> byId = new HashMap<>();
        b.passengers().forEach(p -> byId.put(p.id(), p));
        for (UUID id : req.passengerIds()) {
            if (!byId.containsKey(id)) throw ApiException.notFound("Passenger", id);
        }
        BookingClient.Booking updated = bookings.markCheckedIn(b.pnr(), req.passengerIds());
        Map<UUID, BookingClient.Passenger> seated = new HashMap<>();
        updated.passengers().forEach(p -> seated.put(p.id(), p));

        List<Map<String, Object>> issued = new ArrayList<>();
        for (UUID id : req.passengerIds()) {
            if (checkins.findByPassengerId(id).isPresent()) continue;
            BookingClient.Passenger p = seated.get(id);
            int seq = checkins.nextSequence(b.flightId());
            String group = "BUSINESS".equals(p.cabin()) ? "1" : String.valueOf(2 + (seq % 4));
            Checkin c = checkins.save(new Checkin(b.id(), b.pnr(), p.id(), p.firstName(), p.lastName(), b.flightId(),
                    b.flightNumber(), b.origin(), b.destination(), b.departureTime(), p.seatNumber(), p.cabin(),
                    boardingPassNumber(b, seq), seq, group, null, b.departureTime().minus(Duration.ofMinutes(45)),
                    agent ? "AGENT" : "ONLINE", actor == null ? "self" : actor.email()));
            issued.add(Map.of("passengerId", p.id().toString(), "name", p.firstName() + " " + p.lastName(),
                    "seatNumber", c.getSeatNumber(), "boardingPassNumber", c.getBoardingPassNumber(),
                    "email", p.email() == null ? "" : p.email()));
        }
        if (!issued.isEmpty()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("bookingId", b.id().toString());
            payload.put("pnr", b.pnr());
            payload.put("flightId", b.flightId().toString());
            payload.put("flightNumber", b.flightNumber());
            payload.put("origin", b.origin());
            payload.put("destination", b.destination());
            payload.put("departureTime", b.departureTime().toString());
            payload.put("contactEmail", b.contactEmail());
            payload.put("channel", agent ? "AGENT" : "ONLINE");
            payload.put("passengers", issued);
            events.publish(Topics.CHECKIN, EventTypes.CHECKIN_COMPLETED, b.id().toString(), payload);
            if (agent) audit.record("CHECKIN_COMPLETED", "Booking", b.pnr(), issued.size() + " passenger(s) by agent");
        }
        return new CheckinView(updated, eligibility(updated), passes(b.pnr()));
    }

    @Transactional(readOnly = true)
    public Checkin boardingPass(BoardingPassRequest req) {
        bookings.retrieve(req.pnr().toUpperCase(), req.lastName()); // ownership proof
        Checkin c = checkins.findByBoardingPassNumber(req.boardingPassNumber())
                .orElseThrow(() -> ApiException.notFound("Boarding pass", req.boardingPassNumber()));
        if (!c.getPnr().equalsIgnoreCase(req.pnr())) throw ApiException.notFound("Boarding pass", req.boardingPassNumber());
        return c;
    }

    @Transactional(readOnly = true)
    public PageResponse<BoardingPass> list(UUID flightId, Pageable pageable) {
        var page = flightId == null ? checkins.findAllBy(pageable) : checkins.findByFlightId(flightId, pageable);
        return PageResponse.of(page, CheckinService::toPass);
    }

    private List<BoardingPass> passes(String pnr) {
        return checkins.findByPnrOrderByLastName(pnr).stream().map(CheckinService::toPass).toList();
    }

    private void requireOpen(BookingClient.Booking b) {
        Eligibility e = eligibility(b);
        if (!e.open()) throw ApiException.conflict("CHECKIN_NOT_OPEN", e.reason());
    }

    static String boardingPassNumber(BookingClient.Booking b, int seq) {
        return b.flightNumber() + "-" + b.pnr() + "-" + String.format("%03d", seq);
    }

    static BoardingPass toPass(Checkin c) {
        return new BoardingPass(c.getId(), c.getPassengerId(), c.getFirstName() + " " + c.getLastName(), c.getPnr(),
                c.getFlightNumber(), c.getOrigin(), c.getDestination(), c.getDepartureTime(), c.getSeatNumber(),
                c.getCabin(), c.getBoardingPassNumber(), c.getSequenceNumber(), c.getBoardingGroup(), c.getGate(),
                c.getBoardingTime(), c.getCheckedInAt(), c.getChannel());
    }
}
