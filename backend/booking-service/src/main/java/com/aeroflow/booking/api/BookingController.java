package com.aeroflow.booking.api;

import com.aeroflow.booking.api.BookingDtos.*;
import com.aeroflow.booking.domain.BookingStatus;
import com.aeroflow.booking.service.BookingService;
import com.aeroflow.booking.service.SeatService;
import com.aeroflow.common.security.Roles;
import com.aeroflow.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Bookings")
public class BookingController {
    private final BookingService bookings;
    private final SeatService seats;

    public BookingController(BookingService bookings, SeatService seats) {
        this.bookings = bookings;
        this.seats = seats;
    }

    @GetMapping("/api/seat-maps/{flightId}")
    @Operation(summary = "Seat map with occupied seats (public)")
    public SeatMapResponse seatMap(@PathVariable UUID flightId) {
        return seats.seatMap(flightId);
    }

    @PostMapping("/api/bookings/quote")
    public QuoteResponse quote(@Valid @RequestBody QuoteRequest req) {
        return bookings.quote(req);
    }

    @PostMapping("/api/bookings/guest")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a booking (guest or signed-in). Requires Idempotency-Key.")
    public BookingResponse createGuest(@Valid @RequestBody CreateBookingRequest req,
                                       @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return bookings.create(req, key);
    }

    @PostMapping("/api/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a booking as an authenticated passenger or agent. Requires Idempotency-Key.")
    public BookingResponse create(@Valid @RequestBody CreateBookingRequest req,
                                  @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return bookings.create(req, key);
    }

    @PostMapping("/api/bookings/retrieve")
    @Operation(summary = "Retrieve a booking with PNR + passenger last name (public)")
    public BookingResponse retrieve(@Valid @RequestBody RetrieveRequest req) {
        return bookings.retrieve(req.pnr(), req.lastName());
    }

    @PostMapping("/api/bookings/guest/{pnr}/cancel")
    @Operation(summary = "Cancel as a guest (last name verification)")
    public BookingResponse cancelGuest(@PathVariable String pnr, @Valid @RequestBody CancelRequest req) {
        return bookings.cancel(pnr, req);
    }

    @GetMapping("/api/bookings/my")
    public List<BookingResponse> mine() {
        return bookings.mine();
    }

    @GetMapping("/api/bookings/{pnr}")
    public BookingResponse get(@PathVariable String pnr) {
        return bookings.getForCurrentUser(pnr);
    }

    @PostMapping("/api/bookings/{pnr}/cancel")
    public BookingResponse cancel(@PathVariable String pnr, @Valid @RequestBody(required = false) CancelRequest req) {
        return bookings.cancel(pnr, req);
    }

    @GetMapping("/api/bookings")
    @PreAuthorize(Roles.IS_BOOKING_STAFF)
    public PageResponse<BookingResponse> search(@RequestParam(required = false) String q,
                                                @RequestParam(required = false) BookingStatus status,
                                                @RequestParam(required = false) UUID flightId,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return bookings.search(q, status, flightId, from, to,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100), Sort.by("createdAt").descending()));
    }

    @GetMapping("/api/bookings/flight/{flightId}/manifest")
    @PreAuthorize(Roles.IS_BOOKING_STAFF)
    public List<PassengerResponse> manifest(@PathVariable UUID flightId) {
        return bookings.manifest(flightId);
    }

    @PutMapping("/api/bookings/{pnr}/passengers/{passengerId}/seat")
    @PreAuthorize(Roles.IS_BOOKING_STAFF)
    public BookingResponse staffSeatChange(@PathVariable String pnr, @PathVariable UUID passengerId,
                                           @Valid @RequestBody SeatChangeRequest req) {
        return seats.changeSeat(pnr, passengerId, req.seatNumber());
    }
}
