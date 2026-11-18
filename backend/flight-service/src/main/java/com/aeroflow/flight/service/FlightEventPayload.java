package com.aeroflow.flight.service;

import com.aeroflow.flight.domain.Flight;
import java.util.LinkedHashMap;
import java.util.Map;

/** Event contract for flight.* events (consumed by booking, notification and reporting). */
public final class FlightEventPayload {
    private FlightEventPayload() {}

    public static Map<String, Object> of(Flight f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("flightId", f.getId().toString());
        m.put("flightNumber", f.getFlightNumber());
        m.put("origin", f.getOrigin().getCode());
        m.put("originCity", f.getOrigin().getCity());
        m.put("destination", f.getDestination().getCode());
        m.put("destinationCity", f.getDestination().getCity());
        m.put("departureTime", f.getDepartureTime().toString());
        m.put("arrivalTime", f.getArrivalTime().toString());
        m.put("estimatedDepartureTime", f.estimatedDeparture().toString());
        m.put("status", f.getStatus().name());
        m.put("delayMinutes", f.getDelayMinutes());
        m.put("reason", f.getStatusReason());
        m.put("gate", f.getGate());
        m.put("capacity", f.getAircraft().capacity());
        m.put("aircraftModel", f.getAircraft().getModel());
        return m;
    }
}
