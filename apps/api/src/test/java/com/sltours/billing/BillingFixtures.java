package com.sltours.billing;
import com.sltours.billing.model.BillingTypes.*;
import com.sltours.billing.render.DocumentView;
import com.sltours.billing.render.DocumentViews;
import com.sltours.billing.render.PdfRenderer;
import com.sltours.billing.service.BillingCalculator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * The supplied reference invoice (INV-100099, Sample Client, 14-07-2026) as a development fixture. It is rendered directly
 * with its printed number for comparison and is never saved or allocated in any number sequence.
 */
public final class BillingFixtures {
    private BillingFixtures() {}
    public static final UUID FIXTURE_ID = UUID.fromString("00000000-0000-0000-0000-000000100099");

    public static CompanyProfile company() {
        return new CompanyProfile("SL Tours (Pty) Ltd", "SL Tours offers excellent opportunities to explore South Africa.",
            "+27 67 012 7776", "+27 67 012 7776", "tours.st@outlook.com", null, "100 Example Street Cape Town 8001",
            "bundled:sl-tours-logo");
    }
    public static Banking banking() { return new Banking("SL TOURS (PTY) LTD", "FNB SA", "00000000000", null, null, null); }
    public static List<String> terms() { return List.of("Submit proof of payment via WhatsApp or email."); }

    public static Line line(String title, List<Leg> legs, String details, long cents) {
        return new Line(title, legs, details, BigDecimal.ONE, null, cents, LineKind.SERVICE, null, null, null);
    }

    /** Invoice 100099 exactly as printed, with "Dropp off" corrected by the shared label. */
    public static DocumentContent youngLife() {
        return new DocumentContent(new Customer("Sample Client", null, null, null), LocalDate.of(2026, 7, 14), null, null, null, null, "ZAR",
            null, null, null,
            List.of(line("Transfer", List.of(
                new Leg("Cape Town international airport", List.of("Nieuwe Sion Estate")),
                new Leg("Nieuwe Sion Estate", List.of("Kenilworth", "Gugulethu", "Macasgavi", "Crossroads", "Mfuleni"))), null, 285_000)),
            null, false, null, null, Template.STANDARD);
    }

    /** A long, multi-service invoice with a discount, to check page breaks and totals. */
    public static DocumentContent longGroupTrip() {
        List<Line> lines = new ArrayList<>();
        String[] places = {"Kenilworth", "Gugulethu", "Macassar", "Crossroads", "Mfuleni", "Khayelitsha", "Mitchells Plain", "Athlone",
            "Langa", "Bellville", "Durbanville", "Parow"};
        for (int day = 1; day <= 9; day++) {
            List<Leg> legs = List.of(new Leg("Atlantic Marina Apartments, V&A Waterfront", List.of("Cape Town International Airport")),
                new Leg("Cape Town International Airport", Arrays.asList(places).subList(0, 3 + day % 9)));
            lines.add(new Line("Day " + day + ": group transfers", legs, "Driver and guide for the day\n• Bottled water\n• Luggage trailer",
                BigDecimal.valueOf(day % 2 == 0 ? 2 : 1), "vehicle", 185_000 + day * 5_000L, LineKind.SERVICE, null, null, null));
        }
        lines.add(new Line("Less: deposit already paid", List.of(), null, BigDecimal.ONE, null, -500_000L, LineKind.ADJUSTMENT, null, null, null));
        return new DocumentContent(new Customer("Cape Town Marathon Group", "Tina Atkinson", "tina@example.com", "+27 82 000 0000"),
            LocalDate.of(2026, 5, 6), LocalDate.of(2026, 5, 13), LocalDate.of(2026, 5, 19), LocalDate.of(2026, 5, 27), "SLT-2026-EXAMPLE",
            "ZAR", 26, "2 × Toyota Quantum with trailer", "Airport arrivals on three flights; the itinerary is confirmed separately.",
            lines, new Discount(DiscountType.PERCENT, 500, "Group discount"), false, "Thank you for travelling with SL Tours.",
            List.of("Submit proof of payment via WhatsApp or email.", "The remaining balance must be paid before the arrival date."),
            Template.STANDARD);
    }

    public static DocumentView view(DocumentContent c, long number, String watermark) {
        Totals totals = BillingCalculator.compute(c, null);
        return DocumentViews.document(Kind.INVOICE, number, FIXTURE_ID, watermark, c, totals, company(), banking(),
            c.terms() == null ? terms() : c.terms());
    }

    public static PdfRenderer.LogoSource bundledLogo() {
        return ref -> {
            try (var in = BillingFixtures.class.getResourceAsStream("/billing/sl-tours-logo.jpg")) {
                return new PdfRenderer.Logo(in.readAllBytes(), "image/jpeg");
            } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
        };
    }
}
