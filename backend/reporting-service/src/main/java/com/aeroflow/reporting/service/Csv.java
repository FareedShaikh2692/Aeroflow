package com.aeroflow.reporting.service;

import java.util.List;
import java.util.Map;

/** RFC 4180 CSV writer with spreadsheet formula-injection protection. */
public final class Csv {
    private Csv() {}

    public static String write(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        List<String> headers = List.copyOf(rows.get(0).keySet());
        sb.append(String.join(",", headers.stream().map(Csv::escape).toList())).append("\r\n");
        for (Map<String, Object> row : rows) {
            sb.append(String.join(",", headers.stream().map(h -> escape(row.get(h))).toList())).append("\r\n");
        }
        return sb.toString();
    }

    static String escape(Object v) {
        if (v == null) return "";
        String s = v.toString();
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0 && !s.matches("^-?\\d+(\\.\\d+)?$")) s = "'" + s;
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
