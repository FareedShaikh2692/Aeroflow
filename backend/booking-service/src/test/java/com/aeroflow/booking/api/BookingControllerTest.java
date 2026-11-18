package com.aeroflow.booking.api;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aeroflow.booking.service.BookingService;
import com.aeroflow.booking.service.SeatService;
import com.aeroflow.common.security.JwtService;
import com.aeroflow.common.security.SecurityConfig;
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

@WebMvcTest(controllers = {BookingController.class, InternalBookingController.class})
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "aeroflow.security.jwt.secret=test-secret-test-secret-test-secret-0123456789",
        "aeroflow.security.public-paths=/api/seat-maps/**,/api/bookings/retrieve,/api/bookings/guest/**,/api/bookings/quote"})
class BookingControllerTest {
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockBean BookingService bookings;
    @MockBean SeatService seats;

    String token(String... roles) {
        return "Bearer " + jwt.issueAccessToken(UUID.randomUUID(), "u@aeroflow.dev", "U", Set.of(roles));
    }

    @Test
    void guestBookingValidatesPayloadWithFieldErrors() throws Exception {
        mvc.perform(post("/api/bookings/guest").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passengers\":[],\"contactEmail\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='contactEmail')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='flightId')]").exists())
                .andExpect(header().exists("X-Correlation-Id"));
        verifyNoInteractions(bookings);
    }

    @Test
    void malformedJsonReturnsConsistentError() throws Exception {
        mvc.perform(post("/api/bookings/retrieve").contentType(MediaType.APPLICATION_JSON).content("{oops"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void staffSearchRequiresAuthenticationAndStaffRole() throws Exception {
        mvc.perform(get("/api/bookings")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/bookings").header("Authorization", token("PASSENGER"))).andExpect(status().isForbidden());
        when(bookings.search(any(), any(), any(), any(), any(), any()))
                .thenReturn(new com.aeroflow.common.web.PageResponse<>(List.of(), 0, 20, 0, 0));
        mvc.perform(get("/api/bookings").header("Authorization", token("FLIGHT_AGENT"))).andExpect(status().isOk());
    }

    @Test
    void internalEndpointsRequireServiceRole() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(get("/internal/flights/{id}/booking-count", id).header("Authorization", token("SUPER_ADMIN")))
                .andExpect(status().isForbidden());
        when(bookings.activeBookingCount(id)).thenReturn(3L);
        mvc.perform(get("/internal/flights/{id}/booking-count", id).header("Authorization", token("SERVICE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(3));
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        String t = token("ADMIN");
        mvc.perform(get("/api/bookings/my").header("Authorization", t.substring(0, t.length() - 3) + "abc"))
                .andExpect(status().isUnauthorized());
    }
}
