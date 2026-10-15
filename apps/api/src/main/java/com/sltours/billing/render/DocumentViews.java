package com.sltours.billing.render;
import com.sltours.billing.model.BillingTypes.*;
import com.sltours.billing.render.DocumentView.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Builds the printed view of quotations, invoices and receipts from their saved data. */
public final class DocumentViews {
    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private DocumentViews() {}

    public static String prefix(Kind kind) { return kind == Kind.INVOICE ? "INV" : "QUO"; }

    /** File name without extension: INV-100099, QUO-1001, or INV-DRAFT-1a2b3c4d before a number is allocated. */
    public static String filenameBase(Kind kind, Long number, UUID draftId) {
        return number != null ? prefix(kind) + "-" + number : prefix(kind) + "-DRAFT-" + draftId.toString().substring(0, 8);
    }

    /**
     * @param number    the official number, or null for a draft
     * @param watermark "DRAFT", "VOID" or null
     */
    public static DocumentView document(Kind kind, Long number, UUID id, String watermark, DocumentContent c, Totals totals,
                                        CompanyProfile company, Banking banking, List<String> terms) {
        boolean invoice = kind == Kind.INVOICE;
        String currency = c.currency() == null ? "ZAR" : c.currency();
        List<Row> info = new ArrayList<>();
        info.add(new Row("DATE", c.issueDate() == null ? LocalDate.now().format(DATE) : c.issueDate().format(DATE)));
        info.add(new Row("CLIENT", c.customer().name()));
        info.add(new Row(invoice ? "INVOICE NUMBER" : "QUOTATION NUMBER", number == null ? "DRAFT" : String.valueOf(number)));
        add(info, "CONTACT PERSON", c.customer().contactPerson());
        if (c.dueDate() != null) info.add(new Row(invoice ? "DUE DATE" : "VALID UNTIL", c.dueDate().format(DATE)));
        add(info, "BOOKING REFERENCE", c.bookingReference());
        String service = serviceDates(c.serviceStart(), c.serviceEnd());
        if (service != null) info.add(new Row(c.serviceEnd() != null && !c.serviceEnd().equals(c.serviceStart()) ? "SERVICE DATES" : "SERVICE DATE", service));
        if (c.passengers() != null) info.add(new Row("PASSENGERS", String.valueOf(c.passengers())));
        add(info, "VEHICLE", c.vehicle());

        boolean detailed = c.templateOrDefault() == Template.DETAILED;
        boolean adjusted = totals.discount() > 0 || totals.tax() > 0;
        boolean showLineAmounts = detailed || c.lines().size() > 1 || adjusted
            || c.lines().stream().anyMatch(l -> l.kindOrDefault() == LineKind.ADJUSTMENT);
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < c.lines().size(); i++) {
            Line line = c.lines().get(i);
            items.add(new Item(blankToNull(line.title()), pieces(line, detailed, currency),
                showLineAmounts ? MoneyFormat.amount(totals.lineTotals().get(i)) : null));
        }

        List<TotalRow> rows = new ArrayList<>();
        if (adjusted) rows.add(new TotalRow("SUBTOTAL:", MoneyFormat.amount(totals.subtotal()), false));
        if (totals.discount() > 0) {
            Discount d = c.discountOrNone();
            String label = d.label() != null && !d.label().isBlank() ? d.label().trim() : "DISCOUNT";
            if (d.type() == DiscountType.PERCENT) label += " (" + percent(d.value()) + ")";
            rows.add(new TotalRow(label.toUpperCase(Locale.ROOT) + ":", MoneyFormat.amount(-totals.discount()), false));
        }
        if (totals.tax() > 0 || totals.taxRateBasisPoints() != null)
            rows.add(new TotalRow((totals.taxLabel() == null ? "TAX" : totals.taxLabel().toUpperCase(Locale.ROOT)) + " ("
                + percent(totals.taxRateBasisPoints() == null ? 0 : totals.taxRateBasisPoints()) + "):", MoneyFormat.amount(totals.tax()), false));
        rows.add(new TotalRow("GRAND TOTAL:", MoneyFormat.withCurrency(totals.total(), currency), true));

        List<String> notes = new ArrayList<>();
        lines(c.itineraryNotes()).forEach(notes::add);
        lines(c.customerNotes()).forEach(notes::add);

        List<Row> bank = new ArrayList<>();
        if (banking != null) {
            add(bank, "ACCOUNT NAME", banking.accountName());
            add(bank, "BANK", banking.bankName());
            add(bank, "ACCOUNT NUMBER", banking.accountNumber());
            add(bank, "ACCOUNT TYPE", banking.accountType());
            add(bank, "BRANCH CODE", banking.branchCode());
            add(bank, "SWIFT CODE", banking.swiftCode());
        }
        return new DocumentView(invoice ? "INVOICE" : "QUOTATION", watermark, filenameBase(kind, number, id), info,
            "COST - " + MoneyFormat.symbol(currency), items, Math.max(0, 2 - (rows.size() - 1)), rows,
            notes.isEmpty() ? null : "NOTES", notes, bank.isEmpty() ? null : "BANKING DETAILS", bank, null,
            terms == null || terms.isEmpty() ? null : "TERMS AND CONDITIONS", terms == null ? List.of() : terms, company(company));
    }

    public static DocumentView receipt(ReceiptSnapshot r, String watermark) {
        String currency = r.currency();
        List<Row> info = new ArrayList<>();
        info.add(new Row("DATE", r.receiptDate().format(DATE)));
        info.add(new Row("CLIENT", r.customer().name()));
        info.add(new Row("RECEIPT NUMBER", String.valueOf(r.number())));
        info.add(new Row(r.documentKind() == Kind.INVOICE ? "INVOICE NUMBER" : "QUOTATION NUMBER", String.valueOf(r.documentNumber())));
        add(info, "BOOKING REFERENCE", r.bookingReference());
        List<Piece> pieces = new ArrayList<>();
        pieces.add(Piece.labelled("Payment date", r.paidOn().format(DATE)));
        pieces.add(Piece.labelled("Method", methodLabel(r.method())));
        if (r.reference() != null && !r.reference().isBlank()) pieces.add(Piece.labelled("Reference", r.reference()));
        List<Item> items = List.of(new Item("Payment received with thanks", pieces, MoneyFormat.amount(r.amount())));
        List<TotalRow> rows = List.of(
            new TotalRow("INVOICE TOTAL:", MoneyFormat.amount(r.documentTotal()), false),
            new TotalRow("TOTAL PAID TO DATE:", MoneyFormat.amount(r.paidToDate()), false),
            new TotalRow("BALANCE OUTSTANDING:", MoneyFormat.withCurrency(r.balance(), currency), true));
        return new DocumentView("PAYMENT RECEIPT", watermark, "REC-" + r.number(), info, "AMOUNT - " + MoneyFormat.symbol(currency), items,
            0, rows, null, List.of(), null, List.of(), null, null, List.of(), company(r.company()));
    }

    public static String methodLabel(PaymentMethod method) {
        return switch (method) { case EFT -> "EFT / bank transfer"; case CASH -> "Cash"; case CARD -> "Card"; case OTHER -> "Other"; };
    }

    /** "Pick up: …", then "Drop off: …" or a bulleted drop-off list, with a blank line between legs, then free text. */
    static List<Piece> pieces(Line line, boolean detailed, String currency) {
        List<Piece> pieces = new ArrayList<>();
        List<Leg> legs = line.legs() == null ? List.of() : line.legs();
        for (Leg leg : legs) {
            List<String> drops = leg.dropOffs() == null ? List.of() : leg.dropOffs().stream().filter(s -> s != null && !s.isBlank()).map(String::trim).toList();
            boolean hasPickup = leg.pickup() != null && !leg.pickup().isBlank();
            if (!hasPickup && drops.isEmpty()) continue;
            if (!pieces.isEmpty()) pieces.add(Piece.gap());
            if (hasPickup) pieces.add(Piece.labelled("Pick up", leg.pickup().trim()));
            if (drops.size() == 1) pieces.add(Piece.labelled("Drop off", drops.get(0)));
            else if (drops.size() > 1) pieces.add(Piece.bullets("Drop off", drops));
        }
        List<String> details = lines(line.details());
        if (!details.isEmpty() && !pieces.isEmpty()) pieces.add(Piece.gap());
        details.forEach(d -> pieces.add(d.startsWith("• ") || d.startsWith("- ") ? Piece.bullets(null, List.of(d.substring(2).trim())) : Piece.text(d)));
        boolean wholeOne = line.quantity().compareTo(BigDecimal.ONE) == 0;
        if (detailed || !wholeOne) {
            String unit = line.unit() == null || line.unit().isBlank() ? "" : " per " + line.unit().trim();
            pieces.add(Piece.text(quantity(line.quantity()) + " × " + MoneyFormat.withCurrency(line.unitPriceCents(), currency) + unit));
        }
        return pieces;
    }

    static String quantity(BigDecimal q) { return q.stripTrailingZeros().toPlainString(); }

    static String percent(long basisPoints) {
        return BigDecimal.valueOf(basisPoints).movePointLeft(2).stripTrailingZeros().toPlainString() + "%";
    }

    static String serviceDates(LocalDate start, LocalDate end) {
        if (start == null) return null;
        return end == null || end.equals(start) ? start.format(DATE) : start.format(DATE) + " to " + end.format(DATE);
    }

    /** Terms as stored in settings: one per line, ignoring blank lines and leading dashes. */
    public static List<String> termLines(String text) {
        return lines(text).stream().map(t -> t.replaceFirst("^[-•]\\s*", "").trim()).filter(t -> !t.isEmpty()).toList();
    }

    private static Company company(CompanyProfile p) {
        return new Company(p.name(), p.tagline(), p.phone(), p.whatsapp(), p.email(), p.website(), p.address(), p.logoRef());
    }
    private static void add(List<Row> rows, String label, String value) {
        if (value != null && !value.isBlank()) rows.add(new Row(label, value.trim()));
    }
    private static List<String> lines(String text) {
        if (text == null || text.isBlank()) return List.of();
        return Arrays.stream(text.split("\\R")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }
    private static String blankToNull(String v) { return v == null || v.isBlank() ? null : v.trim(); }
}
