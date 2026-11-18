package com.aeroflow.booking.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "booking_passengers")
public class BookingPassenger {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "booking_id") private Booking booking;
    @Column(name = "first_name", nullable = false) private String firstName;
    @Column(name = "last_name", nullable = false) private String lastName;
    @Column(name = "date_of_birth") private LocalDate dateOfBirth;
    private String nationality;
    @Column(name = "passport_number") private String passportNumber;
    private String email;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Cabin cabin;
    @Column(name = "seat_number", nullable = false) private String seatNumber;
    @Column(nullable = false) private BigDecimal fare;
    @Column(name = "checked_in", nullable = false) private boolean checkedIn;
    @Column(name = "checked_in_at") private Instant checkedInAt;

    protected BookingPassenger() {}

    public BookingPassenger(String firstName, String lastName, LocalDate dob, String nationality, String passport,
                            String email, Cabin cabin, String seatNumber, BigDecimal fare) {
        this.id = UUID.randomUUID();
        this.firstName = firstName.trim();
        this.lastName = lastName.trim();
        this.dateOfBirth = dob;
        this.nationality = nationality;
        this.passportNumber = passport;
        this.email = email;
        this.cabin = cabin;
        this.seatNumber = seatNumber;
        this.fare = fare;
    }

    void attach(Booking b) { this.booking = b; }

    public void changeSeat(String seat) { this.seatNumber = seat; }

    public void checkIn() {
        if (!checkedIn) {
            checkedIn = true;
            checkedInAt = Instant.now();
        }
    }

    public String maskedPassport() {
        if (passportNumber == null || passportNumber.length() < 3) return null;
        return "•".repeat(passportNumber.length() - 3) + passportNumber.substring(passportNumber.length() - 3);
    }

    public UUID getId() { return id; }
    public Booking getBooking() { return booking; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public String getNationality() { return nationality; }
    public String getPassportNumber() { return passportNumber; }
    public String getEmail() { return email; }
    public Cabin getCabin() { return cabin; }
    public String getSeatNumber() { return seatNumber; }
    public BigDecimal getFare() { return fare; }
    public boolean isCheckedIn() { return checkedIn; }
    public Instant getCheckedInAt() { return checkedInAt; }
}
