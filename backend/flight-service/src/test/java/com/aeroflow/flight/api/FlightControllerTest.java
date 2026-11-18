package com.aeroflow.flight.api;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aeroflow.common.error.ApiException;
import com.aeroflow.common.security.JwtService;
import com.aeroflow.common.security.SecurityConfig;
import com.aeroflow.flight.service.FlightService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FlightController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {"aeroflow.security.jwt.secret=test-secret-test-secret-test-secret-0123456789",
        "aeroflow.security.public-paths=/api/airports/**,/api/flights/search,/api/flights/public/**"})
class FlightControllerTest {
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockBean FlightService flights;

    String bearer(String role) { return "Bearer " + jwt.issueAccessToken(UUID.randomUUID(), "x@aeroflow.dev", "X", Set.of(role)); }

    @Test
    void publicSearchIsAnonymousAndValidatesParams() throws Exception {
        when(flights.search(any(), any(), any())).thenReturn(List.of());
        mvc.perform(get("/api/flights/search?origin=DXB&destination=LHR&date=2030-01-01")).andExpect(status().isOk());
        mvc.perform(get("/api/flights/search?origin=DUBAI&destination=LHR&date=2030-01-01")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/flights/search?origin=DXB&destination=LHR&date=tomorrow")).andExpect(status().isBadRequest());
    }

    @Test
    void createIsRestrictedToOperationsRoles() throws Exception {
        String body = """
                {"flightNumber":"AF900","originCode":"DXB","destinationCode":"LHR","aircraftId":"%s",
                 "departureTime":"2099-01-01T08:00:00Z","arrivalTime":"2099-01-01T15:00:00Z","economyFare":100,"businessFare":300}
                """.formatted(UUID.randomUUID());
        mvc.perform(post("/api/flights").header("Authorization", bearer("FLIGHT_AGENT")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/flights").header("Authorization", bearer("OPERATIONS_MANAGER")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void invalidFlightNumberAndPastDepartureAreRejected() throws Exception {
        String body = """
                {"flightNumber":"flight-1","originCode":"DXB","destinationCode":"LHR","aircraftId":"%s",
                 "departureTime":"2000-01-01T08:00:00Z","arrivalTime":"2000-01-01T15:00:00Z","economyFare":100,"businessFare":300}
                """.formatted(UUID.randomUUID());
        mvc.perform(post("/api/flights").header("Authorization", bearer("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='flightNumber')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='departureTime')]").exists());
    }

    @Test
    void deleteRequiresAdminAndBusinessErrorsMapToStatus() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(delete("/api/flights/{id}", id).header("Authorization", bearer("OPERATIONS_MANAGER"))).andExpect(status().isForbidden());
        doThrow(ApiException.conflict("FLIGHT_HAS_BOOKINGS", "has bookings")).when(flights).delete(id);
        mvc.perform(delete("/api/flights/{id}", id).header("Authorization", bearer("ADMIN")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FLIGHT_HAS_BOOKINGS"));
    }
}
