package com.aeroflow.checkin.api;

import com.aeroflow.checkin.service.BookingClient;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CheckinDtos {
    private CheckinDtos() {}

    public record RetrieveRequest(@NotBlank @Pattern(regexp = "^[A-Za-z0-9]{6}$", message = "PNR is 6 characters") String pnr,
                                  @NotBlank @Size(max = 80) String lastName) {}

    public record SeatRequest(@NotBlank @Pattern(regexp = "^[A-Za-z0-9]{6}$") String pnr,
                              @NotBlank @Size(max = 80) String lastName,
                              @NotNull UUID passengerId,
                              @NotBlank @Pattern(regexp = "^\\d{1,2}[A-K]$", message = "Invalid seat") String seatNumber) {}

    public record CompleteRequest(@NotBlank @Pattern(regexp = "^[A-Za-z0-9]{6}$") String pnr,
                                  @NotBlank @Size(max = 80) String lastName,
                                  @NotEmpty List<@NotNull UUID> passengerIds,
                                  @AssertTrue(message = "You must accept the dangerous goods declaration") boolean acceptDeclaration) {}

    public record BoardingPassRequest(@NotBlank @Pattern(regexp = "^[A-Za-z0-9]{6}$") String pnr,
                                      @NotBlank @Size(max = 80) String lastName,
                                      @NotBlank @Size(max = 20) String boardingPassNumber) {}

    public record Eligibility(boolean open, Instant opensAt, Instant closesAt, String reason) {}

    public record BoardingPass(UUID id, UUID passengerId, String passengerName, String pnr, String flightNumber,
                               String origin, String destination, Instant departureTime, String seatNumber, String cabin,
                               String boardingPassNumber, int sequenceNumber, String boardingGroup, String gate,
                               Instant boardingTime, Instant checkedInAt, String channel) {}

    public record CheckinView(BookingClient.Booking booking, Eligibility eligibility, List<BoardingPass> boardingPasses) {}
}
