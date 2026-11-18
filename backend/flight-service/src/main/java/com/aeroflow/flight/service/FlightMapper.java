package com.aeroflow.flight.service;

import com.aeroflow.flight.api.FlightDtos.*;
import com.aeroflow.flight.domain.Aircraft;
import com.aeroflow.flight.domain.Airport;
import com.aeroflow.flight.domain.Flight;

public final class FlightMapper {
    private FlightMapper() {}

    public static AirportResponse toResponse(Airport a) {
        return new AirportResponse(a.getCode(), a.getName(), a.getCity(), a.getCountry(), a.getTimezone());
    }

    public static AircraftResponse toResponse(Aircraft a) {
        return new AircraftResponse(a.getId(), a.getRegistration(), a.getModel(), a.getManufacturer(), a.capacity(),
                a.businessSeats(), a.economySeats(), a.getStatus(),
                new SeatLayout(a.getBusinessRows(), a.getBusinessLayout(), a.getEconomyRows(), a.getEconomyLayout()));
    }

    public static FlightResponse toResponse(Flight f) {
        return new FlightResponse(f.getId(), f.getFlightNumber(), toResponse(f.getOrigin()), toResponse(f.getDestination()),
                toResponse(f.getAircraft()), f.getDepartureTime(), f.getArrivalTime(), f.estimatedDeparture(),
                f.durationMinutes(), f.getAircraft().capacity(), f.getStatus(), f.getDelayMinutes(), f.getStatusReason(),
                f.getGate(), f.getTerminal(), f.getEconomyFare(), f.getBusinessFare(), f.getCurrency(), f.getVersion(),
                f.getUpdatedAt());
    }
}
