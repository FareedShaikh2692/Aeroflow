package com.aeroflow.booking.service;

import com.aeroflow.booking.api.BookingDtos.*;
import com.aeroflow.booking.domain.Booking;
import com.aeroflow.booking.domain.BookingPassenger;
import com.aeroflow.booking.domain.Payment;
import java.util.List;

public final class BookingMapper {
    private BookingMapper() {}

    public static BookingResponse toResponse(Booking b, List<Payment> payments) {
        return new BookingResponse(b.getId(), b.getPnr(), b.getFlightId(), b.getFlightNumber(), b.getOrigin(),
                b.getDestination(), b.getDepartureTime(), b.getArrivalTime(), b.getStatus(), b.getTotalAmount(),
                b.getCurrency(), b.getContactEmail(), b.getContactPhone(), b.getPaymentReference(), b.getCardBrand(),
                b.getCardLast4(), b.getChannel(), b.getCancellationReason(), b.getCancelledAt(), b.getCreatedAt(),
                b.getPassengers().stream().map(BookingMapper::toResponse).toList(),
                payments.stream().map(p -> new PaymentResponse(p.getType(), p.getAmount(), p.getStatus(),
                        p.getProviderRef(), p.getCreatedAt())).toList());
    }

    public static PassengerResponse toResponse(BookingPassenger p) {
        return new PassengerResponse(p.getId(), p.getFirstName(), p.getLastName(), p.getDateOfBirth(), p.getNationality(),
                p.maskedPassport(), p.getEmail(), p.getCabin(), p.getSeatNumber(), p.getFare(), p.isCheckedIn(),
                p.getCheckedInAt());
    }
}
