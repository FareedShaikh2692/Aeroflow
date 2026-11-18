package com.aeroflow.booking.api;

import com.aeroflow.booking.api.BookingDtos.*;
import com.aeroflow.booking.service.BookingService;
import com.aeroflow.booking.service.SeatService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

/** Service-to-service endpoints (ROLE_SERVICE only, blocked at the gateway). */
@Hidden
@RestController
@RequestMapping("/internal")
public class InternalBookingController {
    private final BookingService bookings;
    private final SeatService seats;

    public InternalBookingController(BookingService bookings, SeatService seats) {
        this.bookings = bookings;
        this.seats = seats;
    }

    @GetMapping("/flights/{flightId}/booking-count")
    public Map<String, Long> count(@PathVariable UUID flightId) {
        return Map.of("count", bookings.activeBookingCount(flightId));
    }

    @PostMapping("/bookings/retrieve")
    public BookingResponse retrieve(@Valid @RequestBody RetrieveRequest req) {
        return bookings.retrieve(req.pnr(), req.lastName());
    }

    @GetMapping("/bookings")
    public List<BookingResponse> byEmail(@RequestParam String email) {
        return bookings.byEmail(email);
    }

    @PutMapping("/bookings/{pnr}/passengers/{passengerId}/seat")
    public BookingResponse seat(@PathVariable String pnr, @PathVariable UUID passengerId,
                                @Valid @RequestBody SeatChangeRequest req) {
        return seats.changeSeat(pnr, passengerId, req.seatNumber());
    }

    @PostMapping("/bookings/{pnr}/check-in")
    public BookingResponse checkIn(@PathVariable String pnr, @Valid @RequestBody CheckinMarkRequest req) {
        return seats.markCheckedIn(pnr, req.passengerIds());
    }
}
