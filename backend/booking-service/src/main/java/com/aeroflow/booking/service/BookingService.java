package com.aeroflow.booking.service;

import com.aeroflow.booking.api.BookingDtos.*;
import com.aeroflow.booking.domain.*;
import com.aeroflow.booking.repo.*;
import com.aeroflow.common.audit.AuditService;
import com.aeroflow.common.error.ApiException;
import com.aeroflow.common.events.DomainEventPublisher;
import com.aeroflow.common.events.EventTypes;
import com.aeroflow.common.events.Topics;
import com.aeroflow.common.security.AuthenticatedUser;
import com.aeroflow.common.security.CurrentUser;
import com.aeroflow.common.web.PageResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Booking aggregate operations. Concurrency model:
 * <ol>
 *   <li>the flight's inventory row is locked with SELECT ... FOR UPDATE, serialising seat changes per flight;</li>
 *   <li>seat availability is re-checked under that lock;</li>
 *   <li>a partial unique index on (flight_id, seat_number) rejects any duplicate that slips through.</li>
 * </ol>
 * Idempotency-Key replays return the original booking instead of charging twice.
 */
@Service
public class BookingService {
    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingRepository bookings;
    private final FlightInventoryRepository inventory;
    private final SeatAssignmentRepository seats;
    private final PaymentRepository payments;
    private final IdempotencyKeyRepository idempotency;
    private final FlightClient flights;
    private final PaymentGateway psp;
    private final PnrGenerator pnrs;
    private final DomainEventPublisher events;
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final int minMinutesBeforeDeparture;

    public BookingService(BookingRepository bookings, FlightInventoryRepository inventory, SeatAssignmentRepository seats,
                          PaymentRepository payments, IdempotencyKeyRepository idempotency, FlightClient flights,
                          PaymentGateway psp, PnrGenerator pnrs, DomainEventPublisher events, AuditService audit,
                          ObjectMapper mapper, Clock clock,
                          @Value("${aeroflow.booking.min-minutes-before-departure:45}") int minMinutesBeforeDeparture) {
        this.bookings = bookings;
        this.inventory = inventory;
        this.seats = seats;
        this.payments = payments;
        this.idempotency = idempotency;
        this.flights = flights;
        this.psp = psp;
        this.pnrs = pnrs;
        this.events = events;
        this.audit = audit;
        this.mapper = mapper;
        this.clock = clock;
        this.minMinutesBeforeDeparture = minMinutesBeforeDeparture;
    }

    @Transactional(readOnly = true)
    public QuoteResponse quote(QuoteRequest req) {
        FlightClient.Flight f = flights.get(req.flightId());
        List<BigDecimal> fares = req.cabins().stream().map(c -> fare(f, c)).toList();
        return new QuoteResponse(f.id(), fares.stream().reduce(BigDecimal.ZERO, BigDecimal::add), f.currency(), fares);
    }

    @Transactional
    public BookingResponse create(CreateBookingRequest req, String idempotencyKey) {
        if (idempotencyKey == null || !idempotencyKey.matches("^[A-Za-z0-9_-]{8,80}$")) {
            throw ApiException.badRequest("IDEMPOTENCY_KEY_REQUIRED", "A valid Idempotency-Key header is required");
        }
        String hash = requestHash(req);
        Optional<IdempotencyKey> existing = idempotency.findById(idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().getRequestHash().equals(hash)) {
                throw ApiException.unprocessable("IDEMPOTENCY_KEY_REUSED", "Idempotency-Key was used with a different request");
            }
            log.info("Idempotent replay for key {}", idempotencyKey);
            return toResponse(bookings.findById(existing.get().getBookingId()).orElseThrow());
        }

        FlightClient.Flight flight = flights.get(req.flightId());
        assertBookable(flight);
        SeatLayout layout = flight.seatLayout();

        inventory.insertIfAbsent(flight.id(), flight.flightNumber(), layout.capacity(), flight.status());
        FlightInventory inv = inventory.lockById(flight.id()).orElseThrow();
        if ("CANCELLED".equals(inv.getStatus())) throw ApiException.conflict("FLIGHT_NOT_BOOKABLE", "Flight is cancelled");
        if (inv.available() < req.passengers().size()) {
            throw ApiException.conflict("INSUFFICIENT_SEATS", "Only " + inv.available() + " seat(s) left on this flight");
        }

        List<String> chosen = allocateSeats(req.passengers(), layout, new HashSet<>(seats.occupiedSeats(flight.id())));

        AuthenticatedUser actor = CurrentUser.get().orElse(null);
        boolean staff = actor != null && actor.isStaff();
        UUID owner = actor != null && !staff ? actor.id() : null;
        Booking booking = new Booking(pnrs.next(bookings::existsByPnr),
                new Booking.FlightSnapshot(flight.id(), flight.flightNumber(), flight.origin().code(),
                        flight.destination().code(), flight.departureTime(), flight.arrivalTime()),
                owner, req.contactEmail(), req.contactPhone(), flight.currency(), staff ? "AGENT" : "WEB",
                actor == null ? "guest" : actor.email());
        for (int i = 0; i < req.passengers().size(); i++) {
            PassengerRequest p = req.passengers().get(i);
            booking.addPassenger(new BookingPassenger(p.firstName(), p.lastName(), p.dateOfBirth(), p.nationality(),
                    p.passportNumber(), p.email(), p.cabin(), chosen.get(i), fare(flight, p.cabin())));
        }

        PaymentRequest pay = req.payment();
        PaymentGateway.ChargeResult charge = psp.charge(booking.getTotalAmount(), booking.getCurrency(),
                new PaymentGateway.CardDetails(pay.cardNumber(), pay.expiry(), pay.cvv(), pay.cardHolder()), idempotencyKey);
        if (!charge.approved()) {
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "PAYMENT_DECLINED", "Payment declined: " + charge.declineReason());
        }
        booking.recordPayment(charge.reference(), charge.last4(), charge.brand());

        bookings.save(booking);
        booking.getPassengers().forEach(p -> seats.save(new SeatAssignment(flight.id(), p.getSeatNumber(), p.getId())));
        inv.reserve(booking.getPassengers().size());
        payments.save(new Payment(booking.getId(), "CHARGE", booking.getTotalAmount(), booking.getCurrency(), "CAPTURED",
                charge.reference()));
        idempotency.save(new IdempotencyKey(idempotencyKey, hash, booking.getId()));
        bookings.flush(); // surface unique-index violations inside this method

        events.publish(Topics.BOOKING, EventTypes.BOOKING_CONFIRMED, booking.getId().toString(), BookingEvents.payload(booking));
        if (staff) audit.record("BOOKING_CREATED", "Booking", booking.getPnr(), booking.getFlightNumber() + " x"
                + booking.getPassengers().size());
        log.info("Booking {} confirmed on {} for {} passenger(s)", booking.getPnr(), booking.getFlightNumber(),
                booking.getPassengers().size());
        return toResponse(booking);
    }

    /** Validates requested seats and auto-assigns the first free seat in-cabin for passengers without one. */
    static List<String> allocateSeats(List<PassengerRequest> pax, SeatLayout layout, Set<String> occupied) {
        Set<String> taken = new HashSet<>(occupied);
        List<String> conflicts = new ArrayList<>();
        String[] result = new String[pax.size()];
        for (int i = 0; i < pax.size(); i++) {
            String seat = pax.get(i).seatNumber();
            if (seat == null || seat.isBlank()) continue;
            seat = seat.trim().toUpperCase();
            Cabin cabin = layout.cabinOf(seat).orElseThrow(() -> ApiException.badRequest("INVALID_SEAT", "Seat does not exist"));
            if (cabin != pax.get(i).cabin()) {
                throw ApiException.badRequest("CABIN_MISMATCH", "Seat " + pax.get(i).seatNumber() + " is not in " + pax.get(i).cabin());
            }
            if (!taken.add(seat)) conflicts.add(seat);
            result[i] = seat;
        }
        if (!conflicts.isEmpty()) {
            throw ApiException.conflict("SEAT_UNAVAILABLE", "Seat(s) no longer available: " + String.join(", ", conflicts));
        }
        for (int i = 0; i < pax.size(); i++) {
            if (result[i] != null) continue;
            Cabin cabin = pax.get(i).cabin();
            String free = layout.seats(cabin).stream().filter(s -> !taken.contains(s)).findFirst()
                    .orElseThrow(() -> ApiException.conflict("CABIN_FULL", cabin + " cabin is full"));
            taken.add(free);
            result[i] = free;
        }
        return List.of(result);
    }

    @Transactional(readOnly = true)
    public BookingResponse retrieve(String pnr, String lastName) {
        Booking b = byPnr(pnr);
        if (!b.hasPassengerWithLastName(lastName)) throw ApiException.notFound("Booking", pnr.toUpperCase());
        return toResponse(b);
    }

    @Transactional(readOnly = true)
    public BookingResponse getForCurrentUser(String pnr) {
        Booking b = byPnr(pnr);
        AuthenticatedUser u = CurrentUser.require();
        if (!u.isStaff() && !u.id().equals(b.getUserId()) && !u.email().equalsIgnoreCase(b.getContactEmail())) {
            throw ApiException.notFound("Booking", pnr);
        }
        return toResponse(b);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> mine() {
        AuthenticatedUser u = CurrentUser.require();
        Map<UUID, Booking> all = new LinkedHashMap<>();
        bookings.findByUserIdOrderByDepartureTimeDesc(u.id()).forEach(b -> all.put(b.getId(), b));
        bookings.findByContactEmailIgnoreCaseOrderByDepartureTimeDesc(u.email()).forEach(b -> all.putIfAbsent(b.getId(), b));
        return all.values().stream().sorted(Comparator.comparing(Booking::getDepartureTime).reversed())
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> byEmail(String email) {
        return bookings.findByContactEmailIgnoreCaseOrderByDepartureTimeDesc(email).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> search(String q, BookingStatus status, UUID flightId, LocalDate from,
                                                LocalDate to, Pageable pageable) {
        Specification<Booking> spec = (r, cq, cb) -> cb.conjunction();
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase() + "%";
            spec = spec.and((r, cq, cb) -> cb.or(cb.like(cb.lower(r.get("pnr")), like),
                    cb.like(cb.lower(r.get("contactEmail")), like), cb.like(cb.lower(r.get("flightNumber")), like)));
        }
        if (status != null) spec = spec.and((r, cq, cb) -> cb.equal(r.get("status"), status));
        if (flightId != null) spec = spec.and((r, cq, cb) -> cb.equal(r.get("flightId"), flightId));
        if (from != null) {
            Instant f = from.atStartOfDay(ZoneOffset.UTC).toInstant();
            spec = spec.and((r, cq, cb) -> cb.greaterThanOrEqualTo(r.get("createdAt"), f));
        }
        if (to != null) {
            Instant t = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            spec = spec.and((r, cq, cb) -> cb.lessThan(r.get("createdAt"), t));
        }
        return PageResponse.of(bookings.findAll(spec, pageable), this::toResponse);
    }

    /** Cancels by owner, staff, or a guest who proves the passenger last name. Releases seats and refunds. */
    @Transactional
    public BookingResponse cancel(String pnr, CancelRequest req) {
        Booking b = byPnr(pnr);
        AuthenticatedUser u = CurrentUser.get().orElse(null);
        boolean staff = u != null && u.isStaff();
        boolean owner = u != null && (u.id().equals(b.getUserId()) || u.email().equalsIgnoreCase(b.getContactEmail()));
        boolean guestVerified = req != null && b.hasPassengerWithLastName(req.lastName());
        if (!staff && !owner && !guestVerified) throw ApiException.notFound("Booking", pnr);
        if (b.getStatus() == BookingStatus.CANCELLED) throw ApiException.conflict("ALREADY_CANCELLED", "Booking is already cancelled");
        if (b.anyCheckedIn() && !staff) {
            throw ApiException.conflict("CHECKED_IN", "Checked-in bookings can only be cancelled by airline staff");
        }
        if (b.getDepartureTime().isBefore(Instant.now(clock))) {
            throw ApiException.conflict("FLIGHT_DEPARTED", "Bookings cannot be cancelled after departure");
        }
        String reason = req == null || req.reason() == null || req.reason().isBlank() ? "Cancelled by customer" : req.reason();
        doCancel(b, reason, false);
        if (staff) audit.record("BOOKING_CANCELLED", "Booking", b.getPnr(), reason);
        return toResponse(b);
    }

    /** Shared by customer cancellation and airline-initiated (flight cancelled) cancellation. */
    void doCancel(Booking b, String reason, boolean byAirline) {
        FlightInventory inv = inventory.lockById(b.getFlightId()).orElse(null);
        b.cancel(reason);
        b.getPassengers().forEach(p -> seats.findByPassengerIdAndReleasedFalse(p.getId()).ifPresent(SeatAssignment::release));
        if (inv != null) inv.release(b.getPassengers().size());
        String refund = psp.refund(b.getPaymentReference(), b.getTotalAmount(), b.getCurrency());
        payments.save(new Payment(b.getId(), "REFUND", b.getTotalAmount(), b.getCurrency(), "REFUNDED", refund));
        Map<String, Object> payload = new LinkedHashMap<>(BookingEvents.payload(b));
        payload.put("reason", reason);
        payload.put("cancelledByAirline", byAirline);
        payload.put("refundAmount", b.getTotalAmount());
        events.publish(Topics.BOOKING, EventTypes.BOOKING_CANCELLED, b.getId().toString(), payload);
        log.info("Booking {} cancelled ({}), refund {}", b.getPnr(), reason, refund);
    }

    @Transactional(readOnly = true)
    public long activeBookingCount(UUID flightId) {
        return bookings.countByFlightIdAndStatus(flightId, BookingStatus.CONFIRMED);
    }

    @Transactional(readOnly = true)
    public List<PassengerResponse> manifest(UUID flightId) {
        return bookings.findByFlightIdAndStatus(flightId, BookingStatus.CONFIRMED).stream()
                .flatMap(b -> b.getPassengers().stream()).map(BookingMapper::toResponse)
                .sorted(Comparator.comparing(PassengerResponse::seatNumber)).toList();
    }

    BookingResponse toResponse(Booking b) {
        return BookingMapper.toResponse(b, payments.findByBookingIdOrderByCreatedAt(b.getId()));
    }

    Booking byPnr(String pnr) {
        return bookings.findByPnr(pnr.trim().toUpperCase()).orElseThrow(() -> ApiException.notFound("Booking", pnr.toUpperCase()));
    }

    private void assertBookable(FlightClient.Flight f) {
        if (!"SCHEDULED".equals(f.status()) && !"DELAYED".equals(f.status())) {
            throw ApiException.conflict("FLIGHT_NOT_BOOKABLE", "Flight " + f.flightNumber() + " is " + f.status());
        }
        if (f.departureTime().isBefore(Instant.now(clock).plus(Duration.ofMinutes(minMinutesBeforeDeparture)))) {
            throw ApiException.conflict("BOOKING_CLOSED", "Booking closes " + minMinutesBeforeDeparture + " minutes before departure");
        }
    }

    private static BigDecimal fare(FlightClient.Flight f, Cabin cabin) {
        return cabin == Cabin.BUSINESS ? f.businessFare() : f.economyFare();
    }

    private String requestHash(CreateBookingRequest req) {
        try {
            // card data excluded from the hash input beyond last 4 digits
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("flightId", req.flightId());
            m.put("passengers", req.passengers());
            m.put("contactEmail", req.contactEmail());
            String card = req.payment().cardNumber().replaceAll("\\D", "");
            m.put("card", card.length() >= 4 ? card.substring(card.length() - 4) : card);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsString(m).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (JsonProcessingException | java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
