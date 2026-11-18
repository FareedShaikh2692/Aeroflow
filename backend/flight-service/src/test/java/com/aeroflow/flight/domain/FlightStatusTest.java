package com.aeroflow.flight.domain;

import static com.aeroflow.flight.domain.FlightStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FlightStatusTest {
    @ParameterizedTest(name = "{0} -> {1} allowed={2}")
    @CsvSource({"SCHEDULED,DELAYED,true", "SCHEDULED,BOARDING,true", "SCHEDULED,ARRIVED,false", "DELAYED,DELAYED,true",
            "DELAYED,SCHEDULED,true", "BOARDING,DEPARTED,true", "DEPARTED,ARRIVED,true", "DEPARTED,CANCELLED,false",
            "ARRIVED,SCHEDULED,false", "CANCELLED,SCHEDULED,false", "SCHEDULED,SCHEDULED,false"})
    void transitions(FlightStatus from, FlightStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }

    @Test
    void delayAdjustsEstimatedDepartureAndResetClearsIt() {
        Airport dxb = new Airport("DXB", "Dubai", "Dubai", "AE", "Asia/Dubai");
        Airport lhr = new Airport("LHR", "Heathrow", "London", "UK", "Europe/London");
        Aircraft ac = new Aircraft("AF-1", "A350", "Airbus", 2, "AC-DF", 10, "ABC-DEF");
        Instant dep = Instant.parse("2030-01-01T08:00:00Z");
        Flight f = new Flight("af1", dxb, lhr, ac, dep, dep.plusSeconds(7 * 3600), BigDecimal.TEN, BigDecimal.TEN, "AED");
        assertThat(f.getFlightNumber()).isEqualTo("AF1");
        assertThat(f.durationMinutes()).isEqualTo(420);
        assertThat(ac.capacity()).isEqualTo(8 + 60);
        f.changeStatus(DELAYED, 45, "ATC");
        assertThat(f.estimatedDeparture()).isEqualTo(dep.plusSeconds(45 * 60));
        f.changeStatus(SCHEDULED, null, null);
        assertThat(f.getDelayMinutes()).isZero();
        f.changeStatus(CANCELLED, null, "Weather");
        assertThatThrownBy(() -> f.changeStatus(BOARDING, null, null)).isInstanceOf(IllegalStateException.class);
    }
}
