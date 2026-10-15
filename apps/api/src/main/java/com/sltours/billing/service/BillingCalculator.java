package com.sltours.billing.service;
import com.sltours.billing.model.BillingTypes.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Money rules for every document, in whole cents:
 * <ol>
 *   <li>line total = quantity × unit price, rounded half-up to the cent;</li>
 *   <li>subtotal = sum of line totals (adjustment lines may be negative, the subtotal may not);</li>
 *   <li>discount = a fixed amount up to the subtotal, or a percentage of the subtotal rounded half-up;</li>
 *   <li>tax (only when configured) = (subtotal − discount) × rate, rounded half-up; prices are tax-exclusive;</li>
 *   <li>total = subtotal − discount + tax.</li>
 * </ol>
 * Totals sent by a browser are never used.
 */
public final class BillingCalculator {
    public static final long MAX_UNIT_PRICE_CENTS = 1_000_000_000L;   // R10 million per unit
    public static final long MAX_TOTAL_CENTS = 100_000_000_000L;      // R1 billion per document

    /** A tax rate to apply, from settings (drafts) or the snapshot (issued documents). */
    public record TaxRule(int rateBasisPoints, String label) {}

    private BillingCalculator() {}

    public static Totals compute(DocumentContent content, TaxRule tax) {
        if (content.applyTax() && tax == null)
            throw new IllegalArgumentException("Tax is not configured. Ask an administrator to set it up in billing settings, or untick tax.");
        List<Long> lineTotals = new ArrayList<>();
        long subtotal = 0;
        for (int i = 0; i < content.lines().size(); i++) {
            Line line = content.lines().get(i);
            String which = "Service " + (i + 1);
            long unit = line.unitPriceCents();
            if (Math.abs(unit) > MAX_UNIT_PRICE_CENTS) throw new IllegalArgumentException(which + ": the price is too large");
            if (line.kindOrDefault() == LineKind.SERVICE && unit < 0)
                throw new IllegalArgumentException(which + ": a service cannot have a negative price. Use an adjustment line for deductions.");
            if (line.quantity() == null || line.quantity().signum() <= 0) throw new IllegalArgumentException(which + ": enter a quantity");
            long total = line.quantity().multiply(BigDecimal.valueOf(unit)).setScale(0, RoundingMode.HALF_UP).longValueExact();
            lineTotals.add(total);
            subtotal = Math.addExact(subtotal, total);
        }
        if (subtotal < 0) throw new IllegalArgumentException("Adjustments cannot be larger than the services they reduce");
        Discount discount = content.discountOrNone();
        long discountCents = switch (discount.type() == null ? DiscountType.NONE : discount.type()) {
            case NONE -> 0;
            case AMOUNT -> {
                if (discount.value() > subtotal) throw new IllegalArgumentException("The discount cannot be more than the subtotal");
                yield discount.value();
            }
            case PERCENT -> {
                if (discount.value() > 10_000) throw new IllegalArgumentException("A percentage discount cannot be more than 100%");
                yield percentOf(subtotal, discount.value());
            }
        };
        long taxable = subtotal - discountCents;
        long taxCents = content.applyTax() ? percentOf(taxable, tax.rateBasisPoints()) : 0;
        long total = Math.addExact(taxable, taxCents);
        if (total > MAX_TOTAL_CENTS) throw new IllegalArgumentException("The total is too large");
        return new Totals(List.copyOf(lineTotals), subtotal, discountCents, taxable, taxCents, total,
            content.applyTax() ? tax.rateBasisPoints() : null, content.applyTax() ? tax.label() : null);
    }

    /** amount × basisPoints / 10 000, rounded half-up. */
    static long percentOf(long amount, long basisPoints) {
        return BigDecimal.valueOf(amount).multiply(BigDecimal.valueOf(basisPoints))
            .divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP).longValueExact();
    }
}
