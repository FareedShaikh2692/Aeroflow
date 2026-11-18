package com.aeroflow.flight.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "airports")
public class Airport {
    @Id private String code;
    @Column(nullable = false) private String name;
    @Column(nullable = false) private String city;
    @Column(nullable = false) private String country;
    @Column(nullable = false) private String timezone;

    protected Airport() {}
    public Airport(String code, String name, String city, String country, String timezone) {
        this.code = code; this.name = name; this.city = city; this.country = country; this.timezone = timezone;
    }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getCity() { return city; }
    public String getCountry() { return country; }
    public String getTimezone() { return timezone; }
}
