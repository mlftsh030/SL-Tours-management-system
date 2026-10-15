package com.sltours.privatetrips.service;
import java.math.*;
import java.util.*;

/**
 * Hourly private-trip pricing in integer cents. Base price = hourly rate × billable hours, where billable time is
 * the requested duration raised to the minimum and rounded up to the billing increment. Part-cent results round
 * half-up once per line.
 */
public final class PriceCalculator {
    private PriceCalculator() {}
    public record Fee(String name, String type, int amountCents) {}
    public record FeeLine(String name, String type, int amountCents, int totalCents) {}
    public record Breakdown(int hourlyRateCents, int requestedMinutes, int minimumMinutes, int incrementMinutes, int billableMinutes,
        boolean minimumApplied, boolean roundedUp, int baseCents, List<FeeLine> fees, int feesCents, int totalCents) {}

    public static Breakdown price(int hourlyRateCents, int requestedMinutes, int minimumMinutes, int incrementMinutes, List<Fee> fees) {
        if (hourlyRateCents <= 0) throw new IllegalArgumentException("Hourly rate must be positive");
        if (requestedMinutes <= 0 || incrementMinutes <= 0 || minimumMinutes < 0) throw new IllegalArgumentException("Invalid duration");
        int atLeastMinimum = Math.max(requestedMinutes, minimumMinutes);
        int billable = ceilTo(atLeastMinimum, incrementMinutes);
        int base = perHour(hourlyRateCents, billable);
        List<FeeLine> lines = new ArrayList<>();
        long feeTotal = 0;
        for (Fee fee : fees) {
            int total = switch (fee.type()) {
                case "FIXED" -> fee.amountCents();
                case "PER_BILLABLE_HOUR" -> perHour(fee.amountCents(), billable);
                default -> throw new IllegalArgumentException("Unknown fee type " + fee.type());
            };
            lines.add(new FeeLine(fee.name(), fee.type(), fee.amountCents(), total));
            feeTotal += total;
        }
        return new Breakdown(hourlyRateCents, requestedMinutes, minimumMinutes, incrementMinutes, billable, minimumMinutes > requestedMinutes,
            billable > atLeastMinimum, base, List.copyOf(lines), Math.toIntExact(feeTotal), Math.toIntExact(Math.addExact((long) base, feeTotal)));
    }

    static int ceilTo(int minutes, int increment) { return Math.toIntExact(((long) minutes + increment - 1) / increment * increment); }
    static int perHour(int centsPerHour, int minutes) {
        return BigDecimal.valueOf(centsPerHour).multiply(BigDecimal.valueOf(minutes))
            .divide(BigDecimal.valueOf(60), 0, RoundingMode.HALF_UP).intValueExact();
    }
}
