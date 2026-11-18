package com.aeroflow.booking.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aeroflow.booking.domain.Cabin;
import org.junit.jupiter.api.Test;

class SeatLayoutTest {
    private final SeatLayout layout = new SeatLayout(2, "AC-DF", 3, "ABC-DEF");

    @Test
    void computesCapacityPerCabin() {
        assertThat(layout.capacity(Cabin.BUSINESS)).isEqualTo(8);
        assertThat(layout.capacity(Cabin.ECONOMY)).isEqualTo(18);
        assertThat(layout.capacity()).isEqualTo(26);
    }

    @Test
    void resolvesCabinOfSeat() {
        assertThat(layout.cabinOf("1A")).contains(Cabin.BUSINESS);
        assertThat(layout.cabinOf("3b")).contains(Cabin.ECONOMY);
        assertThat(layout.cabinOf("1B")).isEmpty();   // letter not in business layout
        assertThat(layout.cabinOf("6A")).isEmpty();   // beyond last row
        assertThat(layout.cabinOf("0A")).isEmpty();
        assertThat(layout.cabinOf("XX")).isEmpty();
        assertThat(layout.cabinOf(null)).isEmpty();
    }

    @Test
    void listsSeatsInRowOrder() {
        assertThat(layout.seats(Cabin.BUSINESS)).containsExactly("1A", "1C", "1D", "1F", "2A", "2C", "2D", "2F");
        assertThat(layout.seats(Cabin.ECONOMY)).startsWith("3A", "3B").endsWith("5F");
    }
}
