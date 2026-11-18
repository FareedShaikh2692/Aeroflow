package com.aeroflow.booking.service;

import com.aeroflow.booking.api.BookingDtos.*;
import com.aeroflow.booking.domain.*;
import com.aeroflow.booking.repo.*;
import com.aeroflow.common.error.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Seat maps, seat changes and check-in marking (all under the per-flight inventory lock). */
@Service
public class SeatService {
    private final FlightClient flights;
    private final SeatAssignmentRepository seats;
    private final FlightInventoryRepository inventory;
    private final BookingService bookingService;

    public SeatService(FlightClient flights, SeatAssignmentRepository seats, FlightInventoryRepository inventory,
                       BookingService bookingService) {
        this.flights = flights;
        this.seats = seats;
        this.inventory = inventory;
        this.bookingService = bookingService;
    }

    @Transactional(readOnly = true)
    public SeatMapResponse seatMap(UUID flightId) {
        FlightClient.Flight f = flights.get(flightId);
        SeatLayout l = f.seatLayout();
        List<String> occupied = seats.occupiedSeats(flightId);
        return new SeatMapResponse(f.id(), f.flightNumber(), f.aircraft().model(), l.businessRows(), l.businessLayout(),
                l.economyRows(), l.economyLayout(), occupied, f.economyFare(), f.businessFare(), f.currency(),
                l.capacity() - occupied.size());
    }

    @Transactional
    public BookingResponse changeSeat(String pnr, UUID passengerId, String requestedSeat) {
        Booking b = bookingService.byPnr(pnr);
        if (b.getStatus() != BookingStatus.CONFIRMED) throw ApiException.conflict("BOOKING_CANCELLED", "Booking is cancelled");
        BookingPassenger p = b.getPassengers().stream().filter(x -> x.getId().equals(passengerId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Passenger", passengerId));
        if (p.isCheckedIn()) throw ApiException.conflict("ALREADY_CHECKED_IN", "Seat cannot change after check-in");
        String seat = requestedSeat.trim().toUpperCase();
        if (seat.equals(p.getSeatNumber())) return bookingService.toResponse(b);
        SeatLayout layout = flights.get(b.getFlightId()).seatLayout();
        Cabin cabin = layout.cabinOf(seat).orElseThrow(() -> ApiException.badRequest("INVALID_SEAT", "Seat does not exist"));
        if (cabin != p.getCabin()) throw ApiException.badRequest("CABIN_MISMATCH", "Seat " + seat + " is not in " + p.getCabin());

        inventory.lockById(b.getFlightId()).orElseThrow();
        if (seats.occupiedSeats(b.getFlightId()).contains(seat)) {
            throw ApiException.conflict("SEAT_UNAVAILABLE", "Seat " + seat + " is no longer available");
        }
        seats.findByPassengerIdAndReleasedFalse(p.getId()).ifPresent(SeatAssignment::release);
        seats.flush();
        seats.saveAndFlush(new SeatAssignment(b.getFlightId(), seat, p.getId()));
        p.changeSeat(seat);
        return bookingService.toResponse(b);
    }

    @Transactional
    public BookingResponse markCheckedIn(String pnr, List<UUID> passengerIds) {
        Booking b = bookingService.byPnr(pnr);
        if (b.getStatus() != BookingStatus.CONFIRMED) throw ApiException.conflict("BOOKING_CANCELLED", "Booking is cancelled");
        if (b.getDepartureTime().isBefore(Instant.now())) throw ApiException.conflict("FLIGHT_DEPARTED", "Flight has departed");
        for (UUID id : passengerIds) {
            b.getPassengers().stream().filter(p -> p.getId().equals(id)).findFirst()
                    .orElseThrow(() -> ApiException.notFound("Passenger", id)).checkIn();
        }
        return bookingService.toResponse(b);
    }
}
