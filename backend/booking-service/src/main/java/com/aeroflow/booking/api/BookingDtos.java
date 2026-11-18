package com.aeroflow.booking.api;

import com.aeroflow.booking.domain.BookingStatus;
import com.aeroflow.booking.domain.Cabin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class BookingDtos {
    private BookingDtos() {}

    public record PassengerRequest(@NotBlank @Size(max = 80) @Pattern(regexp = "^[\\p{L} .'-]+$", message = "Invalid name") String firstName,
                                   @NotBlank @Size(max = 80) @Pattern(regexp = "^[\\p{L} .'-]+$", message = "Invalid name") String lastName,
                                   @Past LocalDate dateOfBirth,
                                   @Pattern(regexp = "^[A-Z]{2}$", message = "Use a 2-letter ISO country code") String nationality,
                                   @Pattern(regexp = "^[A-Z0-9]{5,20}$", message = "Invalid passport number") String passportNumber,
                                   @Email @Size(max = 254) String email,
                                   @NotNull Cabin cabin,
                                   @Pattern(regexp = "^\\d{1,2}[A-K]$", message = "Invalid seat") String seatNumber) {}

    public record PaymentRequest(@NotBlank @Pattern(regexp = "^[0-9 -]{12,23}$", message = "Invalid card number") String cardNumber,
                                 @NotBlank @Pattern(regexp = "^(0[1-9]|1[0-2])/\\d{2}$", message = "Use MM/YY") String expiry,
                                 @NotBlank @Pattern(regexp = "^\\d{3,4}$", message = "Invalid CVV") String cvv,
                                 @NotBlank @Size(max = 100) String cardHolder) {}

    public record CreateBookingRequest(@NotNull UUID flightId,
                                       @NotEmpty @Size(max = 9) List<@Valid PassengerRequest> passengers,
                                       @NotBlank @Email @Size(max = 254) String contactEmail,
                                       @Pattern(regexp = "^\\+?[0-9 ()-]{6,30}$", message = "Invalid phone number") String contactPhone,
                                       @NotNull @Valid PaymentRequest payment) {}

    public record QuoteRequest(@NotNull UUID flightId, @NotEmpty @Size(max = 9) List<@NotNull Cabin> cabins) {}
    public record QuoteResponse(UUID flightId, BigDecimal total, String currency, List<BigDecimal> fares) {}

    public record PassengerResponse(UUID id, String firstName, String lastName, LocalDate dateOfBirth, String nationality,
                                    String passportMasked, String email, Cabin cabin, String seatNumber, BigDecimal fare,
                                    boolean checkedIn, Instant checkedInAt) {}

    public record PaymentResponse(String type, BigDecimal amount, String status, String reference, Instant at) {}

    public record BookingResponse(UUID id, String pnr, UUID flightId, String flightNumber, String origin, String destination,
                                  Instant departureTime, Instant arrivalTime, BookingStatus status, BigDecimal totalAmount,
                                  String currency, String contactEmail, String contactPhone, String paymentReference,
                                  String cardBrand, String cardLast4, String channel, String cancellationReason,
                                  Instant cancelledAt, Instant createdAt, List<PassengerResponse> passengers,
                                  List<PaymentResponse> payments) {}

    public record CancelRequest(@Size(max = 80) String lastName, @Size(max = 300) String reason) {}

    public record RetrieveRequest(@NotBlank @Pattern(regexp = "^[A-Za-z0-9]{6}$", message = "PNR is 6 characters") String pnr,
                                  @NotBlank @Size(max = 80) String lastName) {}

    public record SeatChangeRequest(@NotBlank @Pattern(regexp = "^\\d{1,2}[A-K]$", message = "Invalid seat") String seatNumber) {}

    public record CheckinMarkRequest(@NotEmpty List<@NotNull UUID> passengerIds) {}

    public record SeatMapResponse(UUID flightId, String flightNumber, String aircraftModel, int businessRows,
                                  String businessLayout, int economyRows, String economyLayout, List<String> occupied,
                                  BigDecimal economyFare, BigDecimal businessFare, String currency, int available) {}
}
