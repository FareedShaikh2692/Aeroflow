package com.aeroflow.passenger.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "travel_history")
public class TravelRecord {
    @Id private UUID id;
    @Column(nullable = false) private String email;
    @Column(name = "booking_id", nullable = false) private UUID bookingId;
    @Column(nullable = false) private String pnr;
    @Column(name = "flight_number", nullable = false) private String flightNumber;
    @Column(nullable = false) private String origin;
    @Column(nullable = false) private String destination;
    @Column(name = "departure_time", nullable = false) private Instant departureTime;
    @Column(nullable = false) private String status;
    private BigDecimal amount;
    private String currency;
    @Column(name = "checked_in", nullable = false) private boolean checkedIn;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected TravelRecord() {}

    public TravelRecord(String email, UUID bookingId, String pnr, String flightNumber, String origin, String destination,
                        Instant departureTime, String status, BigDecimal amount, String currency) {
        this.id = UUID.randomUUID();
        this.email = email.toLowerCase();
        this.bookingId = bookingId;
        this.pnr = pnr;
        this.flightNumber = flightNumber;
        this.origin = origin;
        this.destination = destination;
        this.departureTime = departureTime;
        this.status = status;
        this.amount = amount;
        this.currency = currency;
        this.updatedAt = Instant.now();
    }

    public void setStatus(String s) { status = s; updatedAt = Instant.now(); }
    public void markCheckedIn() { checkedIn = true; updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public UUID getBookingId() { return bookingId; }
    public String getPnr() { return pnr; }
    public String getFlightNumber() { return flightNumber; }
    public String getOrigin() { return origin; }
    public String getDestination() { return destination; }
    public Instant getDepartureTime() { return departureTime; }
    public String getStatus() { return status; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public boolean isCheckedIn() { return checkedIn; }
}
