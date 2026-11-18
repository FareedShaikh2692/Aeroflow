package com.aeroflow.flight.service;

import com.aeroflow.common.events.DomainEventPublisher;
import com.aeroflow.common.events.EventTypes;
import com.aeroflow.common.events.Topics;
import com.aeroflow.flight.domain.Flight;
import com.aeroflow.flight.domain.FlightStatus;
import com.aeroflow.flight.repo.FlightRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Advances flights whose times have passed (DEPARTED at estimated departure, ARRIVED at arrival + delay)
 * so the operational picture stays correct without manual updates. Publishes the same status events as
 * manual changes, keeping bookings, reporting and notifications consistent.
 */
@Component
@ConditionalOnProperty(name = "aeroflow.flights.auto-lifecycle", havingValue = "true", matchIfMissing = true)
public class FlightLifecycleJob {
    private static final Logger log = LoggerFactory.getLogger(FlightLifecycleJob.class);
    private final FlightRepository flights;
    private final DomainEventPublisher events;
    private final Clock clock;

    public FlightLifecycleJob(FlightRepository flights, DomainEventPublisher events, Clock clock) {
        this.flights = flights;
        this.events = events;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${aeroflow.flights.lifecycle-interval-ms:300000}", initialDelay = 60000)
    @CacheEvict(value = {"flight", "flightSearch"}, allEntries = true)
    @Transactional
    public int advance() {
        Instant now = Instant.now(clock);
        List<Flight> due = flights.findDueForLifecycle(now, now.minus(Duration.ofDays(3)),
                EnumSet.of(FlightStatus.SCHEDULED, FlightStatus.DELAYED, FlightStatus.BOARDING, FlightStatus.DEPARTED));
        int changed = 0;
        for (Flight f : due) {
            FlightStatus previous = f.getStatus();
            Instant arrival = f.getArrivalTime().plus(Duration.ofMinutes(f.getDelayMinutes()));
            FlightStatus target = arrival.isBefore(now) ? FlightStatus.ARRIVED : FlightStatus.DEPARTED;
            if (previous == target) continue;
            if (target == FlightStatus.ARRIVED && previous != FlightStatus.DEPARTED) f.forceStatus(FlightStatus.DEPARTED, f.getDelayMinutes(), null);
            f.forceStatus(target, f.getDelayMinutes(), null);
            Map<String, Object> payload = new LinkedHashMap<>(FlightEventPayload.of(f));
            payload.put("previousStatus", previous.name());
            events.publish(Topics.FLIGHT, EventTypes.FLIGHT_STATUS_CHANGED, f.getId().toString(), payload);
            changed++;
        }
        if (changed > 0) log.info("Flight lifecycle: {} flight(s) advanced", changed);
        return changed;
    }
}
