package com.aeroflow.booking.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "flight_inventory")
public class FlightInventory {
    @Id @Column(name = "flight_id") private UUID flightId;
    @Column(name = "flight_number", nullable = false) private String flightNumber;
    @Column(nullable = false) private int capacity;
    @Column(name = "booked_seats", nullable = false) private int bookedSeats;
    @Column(nullable = false) private String status;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected FlightInventory() {}

    public FlightInventory(UUID flightId, String flightNumber, int capacity, String status) {
        this.flightId = flightId;
        this.flightNumber = flightNumber;
        this.capacity = capacity;
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public int available() { return capacity - bookedSeats; }

    public void reserve(int seats) {
        if (seats > available()) throw new IllegalStateException("Insufficient seats");
        bookedSeats += seats;
        updatedAt = Instant.now();
    }

    public void release(int seats) {
        bookedSeats = Math.max(0, bookedSeats - seats);
        updatedAt = Instant.now();
    }

    public void sync(String status, int capacity) {
        this.status = status;
        this.capacity = Math.max(capacity, bookedSeats);
        this.updatedAt = Instant.now();
    }

    public UUID getFlightId() { return flightId; }
    public int getCapacity() { return capacity; }
    public int getBookedSeats() { return bookedSeats; }
    public String getStatus() { return status; }
}
