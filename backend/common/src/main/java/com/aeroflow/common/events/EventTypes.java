package com.aeroflow.common.events;

public final class EventTypes {
    public static final String USER_REGISTERED = "user.registered";
    public static final String FLIGHT_CREATED = "flight.created";
    public static final String FLIGHT_UPDATED = "flight.updated";
    public static final String FLIGHT_STATUS_CHANGED = "flight.status-changed";
    public static final String FLIGHT_DELETED = "flight.deleted";
    public static final String BOOKING_CONFIRMED = "booking.confirmed";
    public static final String BOOKING_CANCELLED = "booking.cancelled";
    public static final String BOOKING_FLIGHT_NOTICE = "booking.flight-notice";
    public static final String BOOKING_CHECKIN_REMINDER = "booking.checkin-reminder";
    public static final String CHECKIN_COMPLETED = "checkin.completed";
    public static final String AUDIT_RECORDED = "audit.recorded";
    private EventTypes() {}
}
