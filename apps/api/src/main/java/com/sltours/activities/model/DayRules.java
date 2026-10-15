package com.sltours.activities.model;
import java.time.DayOfWeek;
import java.util.*;

/** Days are stored as comma-separated three-letter codes (MON,TUE,...); null or blank means every day. */
public final class DayRules {
    private DayRules() {}
    private static final List<String> CODES = List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");
    public static boolean matches(String days, DayOfWeek day) {
        if (days == null || days.isBlank()) return true;
        return Arrays.stream(days.split(",")).map(String::trim).anyMatch(code -> code.equals(CODES.get(day.getValue() - 1)));
    }
    public static String normalize(String days) {
        if (days == null || days.isBlank()) return null;
        List<String> codes = Arrays.stream(days.toUpperCase(Locale.ROOT).split(",")).map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
        for (String code : codes) if (!CODES.contains(code)) throw new IllegalArgumentException("Unknown day code: " + code);
        return String.join(",", CODES.stream().filter(codes::contains).toList());
    }
}
