package com.aeroflow.flight.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "aircraft")
public class Aircraft {
    @Id private UUID id;
    @Column(nullable = false, unique = true) private String registration;
    @Column(nullable = false) private String model;
    @Column(nullable = false) private String manufacturer;
    @Column(name = "business_rows", nullable = false) private int businessRows;
    /** Seat letters per row; '-' marks an aisle, e.g. "AC-DF". */
    @Column(name = "business_layout", nullable = false) private String businessLayout;
    @Column(name = "economy_rows", nullable = false) private int economyRows;
    @Column(name = "economy_layout", nullable = false) private String economyLayout;
    @Column(nullable = false) private String status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected Aircraft() {}

    public Aircraft(String registration, String model, String manufacturer, int businessRows, String businessLayout,
                    int economyRows, String economyLayout) {
        this.id = UUID.randomUUID();
        this.registration = registration;
        this.model = model;
        this.manufacturer = manufacturer;
        this.businessRows = businessRows;
        this.businessLayout = businessLayout;
        this.economyRows = economyRows;
        this.economyLayout = economyLayout;
        this.status = "ACTIVE";
        this.createdAt = Instant.now();
    }

    public int businessSeats() { return businessRows * businessLayout.replace("-", "").length(); }
    public int economySeats() { return economyRows * economyLayout.replace("-", "").length(); }
    public int capacity() { return businessSeats() + economySeats(); }

    public UUID getId() { return id; }
    public String getRegistration() { return registration; }
    public String getModel() { return model; }
    public void setModel(String m) { this.model = m; }
    public String getManufacturer() { return manufacturer; }
    public int getBusinessRows() { return businessRows; }
    public String getBusinessLayout() { return businessLayout; }
    public int getEconomyRows() { return economyRows; }
    public String getEconomyLayout() { return economyLayout; }
    public String getStatus() { return status; }
    public void setStatus(String s) { this.status = s; }
}
