package com.aeroflow.flight.api;

import com.aeroflow.flight.domain.FlightStatus;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class FlightDtos {
    private FlightDtos() {}

    public record AirportResponse(String code, String name, String city, String country, String timezone) implements Serializable {}

    public record SeatLayout(int businessRows, String businessLayout, int economyRows, String economyLayout) implements Serializable {}

    public record AircraftResponse(UUID id, String registration, String model, String manufacturer, int capacity,
                                   int businessSeats, int economySeats, String status, SeatLayout layout) implements Serializable {}

    public record FlightResponse(UUID id, String flightNumber, AirportResponse origin, AirportResponse destination,
                                 AircraftResponse aircraft, Instant departureTime, Instant arrivalTime,
                                 Instant estimatedDepartureTime, long durationMinutes, int capacity,
                                 FlightStatus status, int delayMinutes, String statusReason, String gate, String terminal,
                                 BigDecimal economyFare, BigDecimal businessFare, String currency, long version,
                                 Instant updatedAt) implements Serializable {}

    public record FlightRequest(
            @NotBlank @Pattern(regexp = "^[A-Z0-9]{2}\\d{1,4}$", message = "Flight number must look like AF201") String flightNumber,
            @NotBlank @Size(min = 3, max = 3) String originCode,
            @NotBlank @Size(min = 3, max = 3) String destinationCode,
            @NotNull UUID aircraftId,
            @NotNull @Future(message = "Departure must be in the future") Instant departureTime,
            @NotNull Instant arrivalTime,
            @NotNull @DecimalMin("1.00") @Digits(integer = 8, fraction = 2) BigDecimal economyFare,
            @NotNull @DecimalMin("1.00") @Digits(integer = 8, fraction = 2) BigDecimal businessFare,
            @Size(max = 6) String gate,
            @Size(max = 4) String terminal) {}

    public record StatusChangeRequest(@NotNull FlightStatus status,
                                      @Min(0) @Max(1440) Integer delayMinutes,
                                      @Size(max = 300) String reason,
                                      @Size(max = 6) String gate) {}

    public record AircraftRequest(@NotBlank @Pattern(regexp = "^[A-Z0-9-]{3,10}$") String registration,
                                  @NotBlank @Size(max = 60) String model,
                                  @NotBlank @Size(max = 60) String manufacturer,
                                  @Min(0) @Max(20) int businessRows,
                                  @NotBlank @Pattern(regexp = "^[A-K](-?[A-K])*$") String businessLayout,
                                  @Min(1) @Max(70) int economyRows,
                                  @NotBlank @Pattern(regexp = "^[A-K](-?[A-K])*$") String economyLayout) {}

    public record FlightStats(long total, long scheduled, long delayed, long cancelled, long boarding, long departed,
                              long arrived, long activeNow, long departingToday) {}
}
