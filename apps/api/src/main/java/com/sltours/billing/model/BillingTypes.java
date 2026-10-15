package com.sltours.billing.model;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Value types for quotations, invoices and receipts. {@link DocumentContent} is what staff edit on a draft; issuing
 * freezes it, with the totals and the company details of the day, into an {@link IssuedSnapshot}.
 */
public final class BillingTypes {
    private BillingTypes() {}

    public enum Kind { QUOTATION, INVOICE }
    public enum Status { DRAFT, ISSUED, ACCEPTED, DECLINED, VOID }
    public enum PaymentStatus { NOT_APPLICABLE, UNPAID, PARTIALLY_PAID, PAID }
    public enum PaymentMethod { EFT, CASH, CARD, OTHER }
    /** REVERSED: confirmed earlier, then reversed (bounced, charged back or recorded in error); the receipt stays on file. */
    public enum PaymentState { PENDING, CONFIRMED, REJECTED, REVERSED }
    /** SERVICE lines are charges; ADJUSTMENT lines may be negative ("Less: 50% deposit already paid"). */
    public enum LineKind { SERVICE, ADJUSTMENT }
    public enum DiscountType { NONE, AMOUNT, PERCENT }
    /** STANDARD matches the two-column sample; DETAILED adds quantity, unit and unit price to every line. */
    public enum Template { STANDARD, DETAILED }
    public enum Channel { WHATSAPP, EMAIL, NATIVE, LINK }

    public record Customer(
        @NotBlank(message = "Enter the customer or organisation name") @Size(max = 200) String name,
        @Size(max = 160) String contactPerson,
        @Email(message = "Enter a valid email address") @Size(max = 180) String email,
        @Size(max = 40) @Pattern(regexp = "^$|^\\+?[0-9 ()-]{7,40}$", message = "Enter a phone number with digits only, e.g. +27 82 123 4567") String whatsapp) {}

    /** One transfer leg: a pickup and one or more drop-offs (printed as a bulleted list when there are several). */
    public record Leg(@Size(max = 240) String pickup, @Size(max = 20) List<@Size(max = 240) String> dropOffs) {}

    public record Line(
        @Size(max = 160) String title,
        @Valid @Size(max = 12) List<Leg> legs,
        @Size(max = 3000) String details,
        @NotNull @DecimalMin(value = "0.01", message = "Quantity must be at least 0.01") @DecimalMax("10000")
        @Digits(integer = 5, fraction = 2, message = "Use at most two decimals for quantity") BigDecimal quantity,
        @Size(max = 30) String unit,
        @NotNull Long unitPriceCents,
        LineKind kind,
        /** Set when the line comes from the tour catalogue; the catalogue price is looked up by the server. */
        @Size(max = 160) String tourSlug,
        Long cataloguePriceCents,
        @Size(max = 300) String priceOverrideReason) {
        public LineKind kindOrDefault() { return kind == null ? LineKind.SERVICE : kind; }
    }

    public record Discount(DiscountType type, @PositiveOrZero long value, @Size(max = 80) String label) {
        public static final Discount NONE = new Discount(DiscountType.NONE, 0, null);
    }

    public record DocumentContent(
        @Valid @NotNull(message = "Enter the customer") Customer customer,
        LocalDate issueDate,
        /** Due date on invoices; "valid until" on quotations. */
        LocalDate dueDate,
        LocalDate serviceStart,
        LocalDate serviceEnd,
        @Size(max = 40) String bookingReference,
        @Pattern(regexp = "[A-Z]{3}", message = "Use a three-letter currency code such as ZAR") String currency,
        @Min(1) @Max(1000) Integer passengers,
        @Size(max = 160) String vehicle,
        @Size(max = 2000) String itineraryNotes,
        @Valid @NotNull @Size(min = 1, max = 60, message = "Add between 1 and 60 services") List<Line> lines,
        @Valid Discount discount,
        /** Apply the configured tax. Refused unless an administrator has enabled tax in billing settings. */
        boolean applyTax,
        @Size(max = 2000) String customerNotes,
        /** Null uses the default terms from settings when the document is issued. */
        @Size(max = 20) List<@Size(max = 300) String> terms,
        Template template) {
        public Template templateOrDefault() { return template == null ? Template.STANDARD : template; }
        public Discount discountOrNone() { return discount == null ? Discount.NONE : discount; }
    }

    /** Server-calculated money, all in cents. */
    public record Totals(List<Long> lineTotals, long subtotal, long discount, long taxable, long tax, long total,
                         Integer taxRateBasisPoints, String taxLabel) {}

    public record CompanyProfile(String name, String tagline, String phone, String whatsapp, String email, String website,
                                 String address, String logoRef) {}
    public record Banking(String accountName, String bankName, String accountNumber, String accountType, String branchCode,
                          String swiftCode) {}

    /** Everything printed on an issued quotation or invoice. Never changes after issue. */
    public record IssuedSnapshot(Kind kind, long number, LocalDate issueDate, LocalDate dueDate, DocumentContent content,
                                 Totals totals, CompanyProfile company, Banking banking, String paymentInstructions,
                                 List<String> terms, String issuedBy, OffsetDateTime issuedAt) {}

    /** Everything printed on a payment receipt. Never changes after the payment is confirmed. */
    public record ReceiptSnapshot(long number, LocalDate receiptDate, Kind documentKind, long documentNumber, Customer customer,
                                  String bookingReference, String currency, long amount, LocalDate paidOn, PaymentMethod method,
                                  String reference, long documentTotal, long paidToDate, long balance, CompanyProfile company,
                                  String confirmedBy, OffsetDateTime confirmedAt) {}
}
