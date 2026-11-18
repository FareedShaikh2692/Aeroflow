package com.aeroflow.common.security;

import java.util.List;

public final class Roles {
    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    public static final String ADMIN = "ADMIN";
    public static final String OPERATIONS_MANAGER = "OPERATIONS_MANAGER";
    public static final String FLIGHT_AGENT = "FLIGHT_AGENT";
    public static final String CHECKIN_AGENT = "CHECKIN_AGENT";
    public static final String PASSENGER = "PASSENGER";
    /** Machine role carried by service-to-service tokens. */
    public static final String SERVICE = "SERVICE";

    public static final List<String> ALL_HUMAN = List.of(SUPER_ADMIN, ADMIN, OPERATIONS_MANAGER, FLIGHT_AGENT, CHECKIN_AGENT, PASSENGER);
    public static final List<String> STAFF = List.of(SUPER_ADMIN, ADMIN, OPERATIONS_MANAGER, FLIGHT_AGENT, CHECKIN_AGENT);

    // SpEL snippets for @PreAuthorize
    public static final String IS_ADMIN = "hasAnyRole('SUPER_ADMIN','ADMIN')";
    public static final String IS_OPS = "hasAnyRole('SUPER_ADMIN','ADMIN','OPERATIONS_MANAGER')";
    public static final String IS_FLIGHT_STAFF = "hasAnyRole('SUPER_ADMIN','ADMIN','OPERATIONS_MANAGER','FLIGHT_AGENT')";
    public static final String IS_STAFF = "hasAnyRole('SUPER_ADMIN','ADMIN','OPERATIONS_MANAGER','FLIGHT_AGENT','CHECKIN_AGENT')";
    public static final String IS_BOOKING_STAFF = "hasAnyRole('SUPER_ADMIN','ADMIN','OPERATIONS_MANAGER','FLIGHT_AGENT','CHECKIN_AGENT')";
    public static final String IS_CHECKIN_STAFF = "hasAnyRole('SUPER_ADMIN','ADMIN','CHECKIN_AGENT')";

    private Roles() {}

    public static boolean isStaff(java.util.Collection<String> roles) {
        return roles.stream().anyMatch(STAFF::contains);
    }
}
