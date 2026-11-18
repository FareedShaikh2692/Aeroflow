package com.aeroflow.flight.domain;

import java.util.EnumSet;
import java.util.Set;

public enum FlightStatus {
    SCHEDULED, DELAYED, BOARDING, DEPARTED, ARRIVED, CANCELLED;

    /** Allowed operational transitions; terminal states cannot be left. */
    public boolean canTransitionTo(FlightStatus next) {
        if (this == next) return this == DELAYED; // re-delay (update minutes) is allowed
        Set<FlightStatus> allowed = switch (this) {
            case SCHEDULED -> EnumSet.of(DELAYED, BOARDING, CANCELLED);
            case DELAYED -> EnumSet.of(SCHEDULED, BOARDING, CANCELLED);
            case BOARDING -> EnumSet.of(DEPARTED, DELAYED, CANCELLED);
            case DEPARTED -> EnumSet.of(ARRIVED);
            case ARRIVED, CANCELLED -> EnumSet.noneOf(FlightStatus.class);
        };
        return allowed.contains(next);
    }

    public boolean isActive() { return this == BOARDING || this == DEPARTED; }
}
