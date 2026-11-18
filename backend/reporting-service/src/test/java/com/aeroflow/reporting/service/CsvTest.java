package com.aeroflow.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CsvTest {
    @Test
    void writesHeaderAndEscapesSpecialCharacters() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("pnr", "K7Q2MX");
        row.put("note", "a, \"quoted\"\nline");
        row.put("amount", -12.5);
        row.put("empty", null);
        assertThat(Csv.write(List.of(row))).isEqualTo("pnr,note,amount,empty\r\nK7Q2MX,\"a, \"\"quoted\"\"\nline\",-12.5,\r\n");
        assertThat(Csv.write(List.of())).isEmpty();
    }

    @Test
    void neutralisesSpreadsheetFormulaInjection() {
        assertThat(Csv.escape("=HYPERLINK(\"http://evil\")")).startsWith("\"'=");
        assertThat(Csv.escape("+cmd")).isEqualTo("'+cmd");
        assertThat(Csv.escape("@SUM(A1)")).isEqualTo("'@SUM(A1)");
        assertThat(Csv.escape("-42")).isEqualTo("-42");
    }

    @Test
    void rangeDefaultsToLast30DaysAndSwapsReversedDates() {
        Clock clock = Clock.fixed(Instant.parse("2030-06-30T12:00:00Z"), ZoneOffset.UTC);
        ReportService.Range r = ReportService.Range.of(null, null, clock);
        assertThat(r.from()).isEqualTo(Instant.parse("2030-06-01T00:00:00Z"));
        assertThat(r.to()).isEqualTo(Instant.parse("2030-07-01T00:00:00Z"));
        ReportService.Range swapped = ReportService.Range.of(LocalDate.of(2030, 6, 10), LocalDate.of(2030, 6, 1), clock);
        assertThat(swapped.from()).isEqualTo(Instant.parse("2030-06-01T00:00:00Z"));
    }
}
