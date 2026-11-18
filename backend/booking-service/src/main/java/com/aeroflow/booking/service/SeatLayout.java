package com.aeroflow.booking.service;

import com.aeroflow.booking.domain.Cabin;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Seat geometry derived from the aircraft layout (rows + letters, '-' = aisle). */
public record SeatLayout(int businessRows, String businessLayout, int economyRows, String economyLayout) {
    private static final Pattern SEAT = Pattern.compile("^(\\d{1,2})([A-K])$");

    public int totalRows() { return businessRows + economyRows; }

    public Optional<Cabin> cabinOf(String seat) {
        if (seat == null) return Optional.empty();
        Matcher m = SEAT.matcher(seat.trim().toUpperCase());
        if (!m.matches()) return Optional.empty();
        int row = Integer.parseInt(m.group(1));
        String letter = m.group(2);
        if (row >= 1 && row <= businessRows && businessLayout.replace("-", "").contains(letter)) return Optional.of(Cabin.BUSINESS);
        if (row > businessRows && row <= totalRows() && economyLayout.replace("-", "").contains(letter)) return Optional.of(Cabin.ECONOMY);
        return Optional.empty();
    }

    public List<String> seats(Cabin cabin) {
        List<String> out = new ArrayList<>();
        int start = cabin == Cabin.BUSINESS ? 1 : businessRows + 1;
        int end = cabin == Cabin.BUSINESS ? businessRows : totalRows();
        String letters = (cabin == Cabin.BUSINESS ? businessLayout : economyLayout).replace("-", "");
        for (int r = start; r <= end; r++) for (char c : letters.toCharArray()) out.add(r + String.valueOf(c));
        return out;
    }

    public int capacity(Cabin cabin) { return seats(cabin).size(); }
    public int capacity() { return capacity(Cabin.BUSINESS) + capacity(Cabin.ECONOMY); }
}
