package com.aeroflow.passenger.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "passengers")
public class Passenger {
    @Id private UUID id;
    @Column(name = "user_id", unique = true) private UUID userId;
    @Column(name = "first_name", nullable = false) private String firstName;
    @Column(name = "last_name", nullable = false) private String lastName;
    @Column(nullable = false) private String email;
    private String phone;
    @Column(name = "date_of_birth") private LocalDate dateOfBirth;
    private String nationality;
    @Column(name = "passport_number") private String passportNumber;
    @Column(name = "passport_expiry") private LocalDate passportExpiry;
    @Column(name = "address_line") private String addressLine;
    private String city;
    private String country;
    @Column(name = "loyalty_tier", nullable = false) private String loyaltyTier = "BLUE";
    @Column(name = "loyalty_points", nullable = false) private int loyaltyPoints;
    private String notes;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private Long version;

    protected Passenger() {}

    public Passenger(UUID userId, String firstName, String lastName, String email, String phone) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email.trim().toLowerCase();
        this.phone = phone;
        this.createdAt = this.updatedAt = Instant.now();
    }

    @PreUpdate void touch() { updatedAt = Instant.now(); }

    public void addPoints(int points) {
        loyaltyPoints = Math.max(0, loyaltyPoints + points);
        loyaltyTier = loyaltyPoints >= 50_000 ? "GOLD" : loyaltyPoints >= 15_000 ? "SILVER" : "BLUE";
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID v) { userId = v; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String v) { firstName = v; }
    public String getLastName() { return lastName; }
    public void setLastName(String v) { lastName = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { email = v.trim().toLowerCase(); }
    public String getPhone() { return phone; }
    public void setPhone(String v) { phone = v; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate v) { dateOfBirth = v; }
    public String getNationality() { return nationality; }
    public void setNationality(String v) { nationality = v; }
    public String getPassportNumber() { return passportNumber; }
    public void setPassportNumber(String v) { passportNumber = v; }
    public LocalDate getPassportExpiry() { return passportExpiry; }
    public void setPassportExpiry(LocalDate v) { passportExpiry = v; }
    public String getAddressLine() { return addressLine; }
    public void setAddressLine(String v) { addressLine = v; }
    public String getCity() { return city; }
    public void setCity(String v) { city = v; }
    public String getCountry() { return country; }
    public void setCountry(String v) { country = v; }
    public String getLoyaltyTier() { return loyaltyTier; }
    public int getLoyaltyPoints() { return loyaltyPoints; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { notes = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version == null ? 0 : version; }
}
