package com.aeroflow.flight.config;

import com.aeroflow.common.events.DomainEventPublisher;
import com.aeroflow.common.events.EventTypes;
import com.aeroflow.common.events.Topics;
import com.aeroflow.flight.domain.*;
import com.aeroflow.flight.repo.AircraftRepository;
import com.aeroflow.flight.repo.AirportRepository;
import com.aeroflow.flight.repo.FlightRepository;
import com.aeroflow.flight.service.FlightEventPayload;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates a fictional AeroFlow network (DXB hub) relative to the current date so the demo
 * always has past, live and future flights. All data is synthetic.
 */
@Component
@ConditionalOnProperty(name = "aeroflow.seed.flights", havingValue = "true")
public class DemoFlightSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoFlightSeeder.class);
    private final AirportRepository airports;
    private final AircraftRepository aircraft;
    private final FlightRepository flights;
    private final DomainEventPublisher events;
    private final Clock clock;

    public DemoFlightSeeder(AirportRepository airports, AircraftRepository aircraft, FlightRepository flights,
                            DomainEventPublisher events, Clock clock) {
        this.airports = airports;
        this.aircraft = aircraft;
        this.flights = flights;
        this.events = events;
        this.clock = clock;
    }

    /** destination, block minutes, outbound UTC hour, economy fare AED */
    record Route(String dest, int minutes, int depHourUtc, int fare) {}

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (flights.count() > 0) return;
        if (airports.count() == 0) seedAirports();
        List<Aircraft> fleet = aircraft.count() == 0 ? seedFleet() : aircraft.findAll();
        Map<String, Airport> ap = new HashMap<>();
        airports.findAll().forEach(a -> ap.put(a.getCode(), a));

        List<Route> routes = List.of(
                new Route("LHR", 470, 2, 2850), new Route("JFK", 840, 4, 4200), new Route("CDG", 430, 3, 2650),
                new Route("SIN", 445, 22, 2300), new Route("SYD", 840, 21, 4600), new Route("BOM", 190, 5, 950),
                new Route("FRA", 400, 7, 2500), new Route("HKG", 480, 20, 2700), new Route("NRT", 590, 19, 3600),
                new Route("JNB", 500, 8, 2900), new Route("IST", 290, 9, 1650), new Route("LAX", 960, 6, 4900),
                new Route("DEL", 205, 10, 980), new Route("CAI", 230, 11, 1200));
        Random rnd = new Random(42);
        LocalDate today = LocalDate.now(clock);
        Instant now = Instant.now(clock);
        int created = 0;
        for (int day = -7; day <= 30; day++) {
            LocalDate date = today.plusDays(day);
            for (int i = 0; i < routes.size(); i++) {
                Route r = routes.get(i);
                Aircraft ac = fleet.get(i % fleet.size());
                int number = 100 + i * 2;
                Instant out = date.atTime(r.depHourUtc(), (i * 5) % 60).toInstant(ZoneOffset.UTC);
                created += save("AF" + number, ap.get("DXB"), ap.get(r.dest()), ac, out, r, rnd, now);
                Instant back = out.plus(Duration.ofMinutes(r.minutes() + 150));
                created += save("AF" + (number + 1), ap.get(r.dest()), ap.get("DXB"), ac, back, r, rnd, now);
            }
        }
        log.info("Seeded {} demo flights across {} routes", created, routes.size() * 2);
    }

    private int save(String number, Airport from, Airport to, Aircraft ac, Instant dep, Route r, Random rnd, Instant now) {
        Instant arr = dep.plus(Duration.ofMinutes(r.minutes()));
        BigDecimal eco = BigDecimal.valueOf(r.fare() + rnd.nextInt(400)).setScale(2);
        BigDecimal bus = eco.multiply(BigDecimal.valueOf(3.2)).setScale(2, java.math.RoundingMode.HALF_UP);
        Flight f = new Flight(number, from, to, ac, dep, arr, eco, bus, "AED");
        f.setTerminal(from.getCode().equals("DXB") ? "3" : "1");
        f.setGate((char) ('A' + rnd.nextInt(3)) + String.valueOf(1 + rnd.nextInt(30)));
        if (arr.isBefore(now)) {
            f.forceStatus(FlightStatus.ARRIVED, rnd.nextInt(10) < 2 ? 25 : 0, null);
        } else if (dep.isBefore(now)) {
            f.forceStatus(FlightStatus.DEPARTED, 0, null);
        } else if (dep.isBefore(now.plus(Duration.ofMinutes(50)))) {
            f.forceStatus(FlightStatus.BOARDING, 0, null);
        } else {
            int roll = rnd.nextInt(100);
            if (roll < 4 && dep.isBefore(now.plus(Duration.ofDays(3)))) {
                f.forceStatus(FlightStatus.DELAYED, 30 + rnd.nextInt(6) * 15, "Late inbound aircraft");
            } else if (roll == 99) {
                f.forceStatus(FlightStatus.CANCELLED, 0, "Operational constraints");
            }
        }
        flights.save(f);
        events.publish(Topics.FLIGHT, EventTypes.FLIGHT_CREATED, f.getId().toString(), FlightEventPayload.of(f));
        return 1;
    }

    private void seedAirports() {
        airports.saveAll(List.of(
                new Airport("DXB", "Dubai International", "Dubai", "United Arab Emirates", "Asia/Dubai"),
                new Airport("LHR", "Heathrow", "London", "United Kingdom", "Europe/London"),
                new Airport("JFK", "John F. Kennedy International", "New York", "United States", "America/New_York"),
                new Airport("CDG", "Charles de Gaulle", "Paris", "France", "Europe/Paris"),
                new Airport("SIN", "Changi", "Singapore", "Singapore", "Asia/Singapore"),
                new Airport("SYD", "Kingsford Smith", "Sydney", "Australia", "Australia/Sydney"),
                new Airport("BOM", "Chhatrapati Shivaji Maharaj International", "Mumbai", "India", "Asia/Kolkata"),
                new Airport("FRA", "Frankfurt am Main", "Frankfurt", "Germany", "Europe/Berlin"),
                new Airport("HKG", "Hong Kong International", "Hong Kong", "Hong Kong SAR", "Asia/Hong_Kong"),
                new Airport("NRT", "Narita International", "Tokyo", "Japan", "Asia/Tokyo"),
                new Airport("JNB", "O. R. Tambo International", "Johannesburg", "South Africa", "Africa/Johannesburg"),
                new Airport("IST", "Istanbul Airport", "Istanbul", "Türkiye", "Europe/Istanbul"),
                new Airport("LAX", "Los Angeles International", "Los Angeles", "United States", "America/Los_Angeles"),
                new Airport("DEL", "Indira Gandhi International", "Delhi", "India", "Asia/Kolkata"),
                new Airport("CAI", "Cairo International", "Cairo", "Egypt", "Africa/Cairo")));
    }

    private List<Aircraft> seedFleet() {
        return aircraft.saveAll(List.of(
                new Aircraft("AF-A350A", "A350-900", "Airbus", 8, "AC-DF", 30, "ABC-DEF-HJK"),
                new Aircraft("AF-B77WA", "777-300ER", "Boeing", 6, "AC-DF", 34, "ABC-DEF-HJK"),
                new Aircraft("AF-B789A", "787-9", "Boeing", 6, "AC-DF", 24, "ABC-DEF-HJK"),
                new Aircraft("AF-A321A", "A321neo", "Airbus", 3, "AC-DF", 25, "ABC-DEF"),
                new Aircraft("AF-A350B", "A350-900", "Airbus", 8, "AC-DF", 30, "ABC-DEF-HJK"),
                new Aircraft("AF-B77WB", "777-300ER", "Boeing", 6, "AC-DF", 34, "ABC-DEF-HJK"),
                new Aircraft("AF-B789B", "787-9", "Boeing", 6, "AC-DF", 24, "ABC-DEF-HJK")));
    }
}
