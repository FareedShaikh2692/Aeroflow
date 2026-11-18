package com.aeroflow.booking.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "bookings")
public class Booking {
    @Id private UUID id;
    @Column(nullable = false, unique = true) private String pnr;
    @Column(name = "flight_id", nullable = false) private UUID flightId;
    @Column(name = "flight_number", nullable = false) private String flightNumber;
    @Column(nullable = false) private String origin;
    @Column(nullable = false) private String destination;
    @Column(name = "departure_time", nullable = false) private Instant departureTime;
    @Column(name = "arrival_time", nullable = false) private Instant arrivalTime;
    @Column(name = "user_id") private UUID userId;
    @Column(name = "contact_email", nullable = false) private String contactEmail;
    @Column(name = "contact_phone") private String contactPhone;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private BookingStatus status;
    @Column(name = "total_amount", nullable = false) private BigDecimal totalAmount;
    @Column(nullable = false) private String currency;
    @Column(name = "payment_reference") private String paymentReference;
    @Column(name = "card_last4") private String cardLast4;
    @Column(name = "card_brand") private String cardBrand;
    @Column(nullable = false) private String channel;
    @Column(name = "created_by") private String createdBy;
    @Column(name = "cancellation_reason") private String cancellationReason;
    @Column(name = "cancelled_at") private Instant cancelledAt;
    @Column(name = "reminder_sent", nullable = false) private boolean reminderSent;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private Long version;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("lastName, firstName")
    private List<BookingPassenger> passengers = new ArrayList<>();

    protected Booking() {}

    public Booking(String pnr, FlightSnapshot flight, UUID userId, String contactEmail, String contactPhone,
                   String currency, String channel, String createdBy) {
        this.id = UUID.randomUUID();
        this.pnr = pnr;
        this.flightId = flight.id();
        this.flightNumber = flight.flightNumber();
        this.origin = flight.origin();
        this.destination = flight.destination();
        this.departureTime = flight.departureTime();
        this.arrivalTime = flight.arrivalTime();
        this.userId = userId;
        this.contactEmail = contactEmail.trim().toLowerCase();
        this.contactPhone = contactPhone;
        this.status = BookingStatus.CONFIRMED;
        this.totalAmount = BigDecimal.ZERO;
        this.currency = currency;
        this.channel = channel;
        this.createdBy = createdBy;
        this.createdAt = this.updatedAt = Instant.now();
    }

    public record FlightSnapshot(UUID id, String flightNumber, String origin, String destination,
                                 Instant departureTime, Instant arrivalTime) {}

    public void addPassenger(BookingPassenger p) {
        passengers.add(p);
        p.attach(this);
        totalAmount = totalAmount.add(p.getFare());
    }

    public void recordPayment(String reference, String last4, String brand) {
        this.paymentReference = reference;
        this.cardLast4 = last4;
        this.cardBrand = brand;
    }

    public void cancel(String reason) {
        if (status == BookingStatus.CANCELLED) throw new IllegalStateException("Booking is already cancelled");
        this.status = BookingStatus.CANCELLED;
        this.cancellationReason = reason;
        this.cancelledAt = Instant.now();
    }

    public boolean anyCheckedIn() { return passengers.stream().anyMatch(BookingPassenger::isCheckedIn); }

    public boolean hasPassengerWithLastName(String lastName) {
        return lastName != null && passengers.stream().anyMatch(p -> p.getLastName().equalsIgnoreCase(lastName.trim()));
    }

    public void reschedule(Instant departure, Instant arrival) {
        this.departureTime = departure;
        this.arrivalTime = arrival;
    }

    @PreUpdate void touch() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public String getPnr() { return pnr; }
    public UUID getFlightId() { return flightId; }
    public String getFlightNumber() { return flightNumber; }
    public String getOrigin() { return origin; }
    public String getDestination() { return destination; }
    public Instant getDepartureTime() { return departureTime; }
    public Instant getArrivalTime() { return arrivalTime; }
    public UUID getUserId() { return userId; }
    public String getContactEmail() { return contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public BookingStatus getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getCurrency() { return currency; }
    public String getPaymentReference() { return paymentReference; }
    public String getCardLast4() { return cardLast4; }
    public String getCardBrand() { return cardBrand; }
    public String getChannel() { return channel; }
    public String getCreatedBy() { return createdBy; }
    public String getCancellationReason() { return cancellationReason; }
    public Instant getCancelledAt() { return cancelledAt; }
    public boolean isReminderSent() { return reminderSent; }
    public void markReminderSent() { this.reminderSent = true; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version == null ? 0 : version; }
    public List<BookingPassenger> getPassengers() { return passengers; }
}
