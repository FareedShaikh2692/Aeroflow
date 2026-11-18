package com.aeroflow.checkin.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "checkins")
public class Checkin {
    @Id private UUID id;
    @Column(name = "booking_id", nullable = false) private UUID bookingId;
    @Column(nullable = false) private String pnr;
    @Column(name = "passenger_id", nullable = false, unique = true) private UUID passengerId;
    @Column(name = "first_name", nullable = false) private String firstName;
    @Column(name = "last_name", nullable = false) private String lastName;
    @Column(name = "flight_id", nullable = false) private UUID flightId;
    @Column(name = "flight_number", nullable = false) private String flightNumber;
    @Column(nullable = false) private String origin;
    @Column(nullable = false) private String destination;
    @Column(name = "departure_time", nullable = false) private Instant departureTime;
    @Column(name = "seat_number", nullable = false) private String seatNumber;
    @Column(nullable = false) private String cabin;
    @Column(name = "boarding_pass_number", nullable = false, unique = true) private String boardingPassNumber;
    @Column(name = "sequence_number", nullable = false) private int sequenceNumber;
    @Column(name = "boarding_group", nullable = false) private String boardingGroup;
    private String gate;
    @Column(name = "boarding_time", nullable = false) private Instant boardingTime;
    @Column(nullable = false) private String channel;
    @Column(name = "checked_in_by") private String checkedInBy;
    @Column(name = "checked_in_at", nullable = false) private Instant checkedInAt;

    protected Checkin() {}

    public Checkin(UUID bookingId, String pnr, UUID passengerId, String firstName, String lastName, UUID flightId,
                   String flightNumber, String origin, String destination, Instant departureTime, String seatNumber,
                   String cabin, String boardingPassNumber, int sequenceNumber, String boardingGroup, String gate,
                   Instant boardingTime, String channel, String checkedInBy) {
        this.id = UUID.randomUUID();
        this.bookingId = bookingId;
        this.pnr = pnr;
        this.passengerId = passengerId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.flightId = flightId;
        this.flightNumber = flightNumber;
        this.origin = origin;
        this.destination = destination;
        this.departureTime = departureTime;
        this.seatNumber = seatNumber;
        this.cabin = cabin;
        this.boardingPassNumber = boardingPassNumber;
        this.sequenceNumber = sequenceNumber;
        this.boardingGroup = boardingGroup;
        this.gate = gate;
        this.boardingTime = boardingTime;
        this.channel = channel;
        this.checkedInBy = checkedInBy;
        this.checkedInAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getBookingId() { return bookingId; }
    public String getPnr() { return pnr; }
    public UUID getPassengerId() { return passengerId; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public UUID getFlightId() { return flightId; }
    public String getFlightNumber() { return flightNumber; }
    public String getOrigin() { return origin; }
    public String getDestination() { return destination; }
    public Instant getDepartureTime() { return departureTime; }
    public String getSeatNumber() { return seatNumber; }
    public String getCabin() { return cabin; }
    public String getBoardingPassNumber() { return boardingPassNumber; }
    public int getSequenceNumber() { return sequenceNumber; }
    public String getBoardingGroup() { return boardingGroup; }
    public String getGate() { return gate; }
    public Instant getBoardingTime() { return boardingTime; }
    public String getChannel() { return channel; }
    public String getCheckedInBy() { return checkedInBy; }
    public Instant getCheckedInAt() { return checkedInAt; }
}
