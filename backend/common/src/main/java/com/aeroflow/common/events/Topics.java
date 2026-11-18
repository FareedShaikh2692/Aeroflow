package com.aeroflow.common.events;

public final class Topics {
    public static final String USER = "aeroflow.user.events";
    public static final String FLIGHT = "aeroflow.flight.events";
    public static final String BOOKING = "aeroflow.booking.events";
    public static final String CHECKIN = "aeroflow.checkin.events";
    public static final String AUDIT = "aeroflow.audit.events";
    public static final java.util.List<String> ALL = java.util.List.of(USER, FLIGHT, BOOKING, CHECKIN, AUDIT);
    private Topics() {}
}
