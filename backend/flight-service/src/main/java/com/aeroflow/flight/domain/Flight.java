package com.aeroflow.flight.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "flights")
public class Flight {
    @Id private UUID id;
    @Column(name = "flight_number", nullable = false) private String flightNumber;
    @ManyToOne(fetch = FetchType.EAGER, optional = false) @JoinColumn(name = "origin_code") private Airport origin;
    @ManyToOne(fetch = FetchType.EAGER, optional = false) @JoinColumn(name = "destination_code") private Airport destination;
    @ManyToOne(fetch = FetchType.EAGER, optional = false) @JoinColumn(name = "aircraft_id") private Aircraft aircraft;
    @Column(name = "departure_time", nullable = false) private Instant departureTime;
    @Column(name = "arrival_time", nullable = false) private Instant arrivalTime;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private FlightStatus status;
    @Column(name = "delay_minutes", nullable = false) private int delayMinutes;
    @Column(name = "status_reason") private String statusReason;
    private String gate;
    private String terminal;
    @Column(name = "economy_fare", nullable = false) private BigDecimal economyFare;
    @Column(name = "business_fare", nullable = false) private BigDecimal businessFare;
    @Column(nullable = false) private String currency;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private Long version;

    protected Flight() {}

    public Flight(String flightNumber, Airport origin, Airport destination, Aircraft aircraft, Instant departureTime,
                  Instant arrivalTime, BigDecimal economyFare, BigDecimal businessFare, String currency) {
        this.id = UUID.randomUUID();
        this.status = FlightStatus.SCHEDULED;
        this.createdAt = this.updatedAt = Instant.now();
        this.currency = currency;
        update(flightNumber, origin, destination, aircraft, departureTime, arrivalTime, economyFare, businessFare);
    }

    public void update(String flightNumber, Airport origin, Airport destination, Aircraft aircraft, Instant dep,
                       Instant arr, BigDecimal economyFare, BigDecimal businessFare) {
        this.flightNumber = flightNumber.toUpperCase();
        this.origin = origin;
        this.destination = destination;
        this.aircraft = aircraft;
        this.departureTime = dep;
        this.arrivalTime = arr;
        this.economyFare = economyFare;
        this.businessFare = businessFare;
    }

    /** Applies a validated operational status change. */
    public void changeStatus(FlightStatus next, Integer delayMinutes, String reason) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException("Cannot change flight status from " + status + " to " + next);
        }
        this.status = next;
        this.statusReason = reason;
        if (next == FlightStatus.DELAYED) this.delayMinutes = delayMinutes == null ? 0 : delayMinutes;
        if (next == FlightStatus.SCHEDULED) this.delayMinutes = 0;
    }

    @PreUpdate void touch() { this.updatedAt = Instant.now(); }

    public long durationMinutes() { return Duration.between(departureTime, arrivalTime).toMinutes(); }
    public Instant estimatedDeparture() { return departureTime.plus(Duration.ofMinutes(delayMinutes)); }

    public UUID getId() { return id; }
    public String getFlightNumber() { return flightNumber; }
    public Airport getOrigin() { return origin; }
    public Airport getDestination() { return destination; }
    public Aircraft getAircraft() { return aircraft; }
    public Instant getDepartureTime() { return departureTime; }
    public Instant getArrivalTime() { return arrivalTime; }
    public FlightStatus getStatus() { return status; }
    public int getDelayMinutes() { return delayMinutes; }
    public String getStatusReason() { return statusReason; }
    public String getGate() { return gate; }
    public void setGate(String g) { this.gate = g; }
    public String getTerminal() { return terminal; }
    public void setTerminal(String t) { this.terminal = t; }
    public BigDecimal getEconomyFare() { return economyFare; }
    public BigDecimal getBusinessFare() { return businessFare; }
    public String getCurrency() { return currency; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version == null ? 0 : version; }
    /** Used only by the demo seeder to back-date historical flights. */
    public void forceStatus(FlightStatus s, int delay, String reason) { this.status = s; this.delayMinutes = delay; this.statusReason = reason; }
}
