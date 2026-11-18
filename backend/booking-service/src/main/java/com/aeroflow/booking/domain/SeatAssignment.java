package com.aeroflow.booking.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "seat_assignments")
public class SeatAssignment {
    @Id private UUID id;
    @Column(name = "flight_id", nullable = false) private UUID flightId;
    @Column(name = "seat_number", nullable = false) private String seatNumber;
    @Column(name = "passenger_id", nullable = false) private UUID passengerId;
    @Column(nullable = false) private boolean released;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "released_at") private Instant releasedAt;

    protected SeatAssignment() {}

    public SeatAssignment(UUID flightId, String seatNumber, UUID passengerId) {
        this.id = UUID.randomUUID();
        this.flightId = flightId;
        this.seatNumber = seatNumber;
        this.passengerId = passengerId;
        this.createdAt = Instant.now();
    }

    public void release() {
        this.released = true;
        this.releasedAt = Instant.now();
    }

    public UUID getFlightId() { return flightId; }
    public String getSeatNumber() { return seatNumber; }
    public UUID getPassengerId() { return passengerId; }
    public boolean isReleased() { return released; }
}
