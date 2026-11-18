package com.aeroflow.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aeroflow.booking.api.BookingDtos.PassengerRequest;
import com.aeroflow.booking.domain.Cabin;
import com.aeroflow.common.error.ApiException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AllocateSeatsTest {
    private final SeatLayout layout = new SeatLayout(1, "AC-DF", 2, "ABC-DEF");

    private static PassengerRequest pax(Cabin cabin, String seat) {
        return new PassengerRequest("Sarah", "Ahmed", null, null, null, null, cabin, seat);
    }

    @Test
    void keepsRequestedSeatsAndAutoAssignsTheRest() {
        List<String> seats = BookingService.allocateSeats(
                List.of(pax(Cabin.ECONOMY, "3c"), pax(Cabin.ECONOMY, null), pax(Cabin.BUSINESS, null)),
                layout, Set.of("2A"));
        assertThat(seats).containsExactly("3C", "2B", "1A");
    }

    @Test
    void rejectsOccupiedSeat() {
        assertThatThrownBy(() -> BookingService.allocateSeats(List.of(pax(Cabin.ECONOMY, "2A")), layout, Set.of("2A")))
                .isInstanceOf(ApiException.class).hasMessageContaining("2A")
                .extracting("code").isEqualTo("SEAT_UNAVAILABLE");
    }

    @Test
    void rejectsSameSeatTwiceInOneRequest() {
        assertThatThrownBy(() -> BookingService.allocateSeats(List.of(pax(Cabin.ECONOMY, "2B"), pax(Cabin.ECONOMY, "2B")),
                layout, Set.of())).extracting("code").isEqualTo("SEAT_UNAVAILABLE");
    }

    @Test
    void rejectsSeatInWrongCabinOrNonExistentSeat() {
        assertThatThrownBy(() -> BookingService.allocateSeats(List.of(pax(Cabin.ECONOMY, "1A")), layout, Set.of()))
                .extracting("code").isEqualTo("CABIN_MISMATCH");
        assertThatThrownBy(() -> BookingService.allocateSeats(List.of(pax(Cabin.ECONOMY, "9Z")), layout, Set.of()))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void failsWhenCabinIsFull() {
        assertThatThrownBy(() -> BookingService.allocateSeats(List.of(pax(Cabin.BUSINESS, null)), layout,
                Set.of("1A", "1C", "1D", "1F"))).extracting("code").isEqualTo("CABIN_FULL");
    }
}
