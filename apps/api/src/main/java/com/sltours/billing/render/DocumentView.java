package com.sltours.billing.render;
import java.util.List;

/**
 * What a document prints, in order, already formatted. The PDF, the Word file and the staff preview are all drawn from
 * this one value, so they cannot disagree about content or totals.
 *
 * @param watermark   "DRAFT" or "VOID" across every page, or null for a valid document
 * @param info        the bordered table under the heading: DATE, CLIENT, INVOICE NUMBER and so on
 * @param items       one block per service line, printed in the DESCRIPTION column
 * @param totals      rows at the foot of the service table; the last one is the grand total
 * @param blankRows   empty rows before the totals, as in the template, so short documents keep its proportions
 */
public record DocumentView(
    String title, String watermark, String filenameBase, List<Row> info, String costHeader, List<Item> items,
    int blankRows, List<TotalRow> totals, String notesHeading, List<String> notes, String bankingHeading, List<Row> banking,
    String paymentInstructions, String termsHeading, List<String> terms, Company company) {

    public record Row(String label, String value) {}

    /** A service line: bold title, then pieces such as "Pick up: …", a bulleted drop-off list or free text. */
    public record Item(String title, List<Piece> pieces, String amount) {}

    public enum PieceKind { LABELLED, BULLETS, TEXT, GAP }
    public record Piece(PieceKind kind, String label, String text, List<String> bullets) {
        public static Piece labelled(String label, String text) { return new Piece(PieceKind.LABELLED, label, text, List.of()); }
        public static Piece bullets(String label, List<String> items) { return new Piece(PieceKind.BULLETS, label, null, items); }
        public static Piece text(String text) { return new Piece(PieceKind.TEXT, null, text, List.of()); }
        public static Piece gap() { return new Piece(PieceKind.GAP, null, null, List.of()); }
    }

    public record TotalRow(String label, String amount, boolean grand) {}

    /** Header and footer: logo, tagline and the contact block printed under the coloured strip. */
    public record Company(String name, String tagline, String phone, String whatsapp, String email, String website, String address,
                          String logoRef) {}
}
