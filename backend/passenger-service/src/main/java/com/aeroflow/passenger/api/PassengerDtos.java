package com.aeroflow.passenger.api;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class PassengerDtos {
    private PassengerDtos() {}

    public record PassengerRequest(
            @NotBlank @Size(max = 80) @Pattern(regexp = "^[\\p{L} .'-]+$", message = "Invalid name") String firstName,
            @NotBlank @Size(max = 80) @Pattern(regexp = "^[\\p{L} .'-]+$", message = "Invalid name") String lastName,
            @NotBlank @Email @Size(max = 254) String email,
            @Pattern(regexp = "^\\+?[0-9 ()-]{6,30}$", message = "Invalid phone number") String phone,
            @Past LocalDate dateOfBirth,
            @Pattern(regexp = "^[A-Z]{2}$", message = "Use a 2-letter ISO country code") String nationality,
            @Pattern(regexp = "^[A-Z0-9]{5,20}$", message = "Invalid passport number") String passportNumber,
            @Future LocalDate passportExpiry,
            @Size(max = 200) String addressLine,
            @Size(max = 80) String city,
            @Size(max = 80) String country,
            @Size(max = 1000) String notes) {}

    public record PassengerResponse(UUID id, UUID userId, String firstName, String lastName, String email, String phone,
                                    LocalDate dateOfBirth, String nationality, String passportNumber,
                                    LocalDate passportExpiry, String addressLine, String city, String country,
                                    String loyaltyTier, int loyaltyPoints, String notes, Instant createdAt,
                                    Instant updatedAt, long version) {}

    public record TravelRecordResponse(UUID bookingId, String pnr, String flightNumber, String origin, String destination,
                                       Instant departureTime, String status, BigDecimal amount, String currency,
                                       boolean checkedIn) {}

    public record PassengerDetail(PassengerResponse profile, java.util.List<TravelRecordResponse> travelHistory,
                                  long totalTrips, long upcomingTrips, BigDecimal lifetimeSpend) {}
}
