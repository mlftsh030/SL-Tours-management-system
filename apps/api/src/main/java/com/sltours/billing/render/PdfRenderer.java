package com.sltours.billing.render;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.sltours.billing.render.DocumentView.*;
import java.io.*;
import java.util.Base64;
import java.util.List;

/**
 * Lays a {@link DocumentView} out as an A4 PDF in the style of SL Tours' Word invoice template: centred logo and italic
 * tagline, orange heading, bordered tables, banking details, terms, and the orange-and-blue strip with the contact
 * block at the foot of every page. Text is real text in embedded fonts (Liberation Sans for Arial, Barlow for the
 * heading), so it can be selected and searched. Long documents continue across pages with the table header repeated.
 */
public final class PdfRenderer {
    /** Resolves a logo reference ("bundled:…" or "media:…") to image bytes. */
    public interface LogoSource { Logo resolve(String ref); }
    public record Logo(byte[] bytes, String contentType) {}

    private static final String FONTS = "/billing/fonts/";
    private final LogoSource logos;
    public PdfRenderer(LogoSource logos) { this.logos = logos; }

    public byte[] render(DocumentView view) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            font(builder, "LiberationSans-Regular.ttf", "Doc Sans", 400, FontStyle.NORMAL);
            font(builder, "LiberationSans-Bold.ttf", "Doc Sans", 700, FontStyle.NORMAL);
            font(builder, "LiberationSans-Italic.ttf", "Doc Sans", 400, FontStyle.ITALIC);
            font(builder, "LiberationSans-BoldItalic.ttf", "Doc Sans", 700, FontStyle.ITALIC);
            font(builder, "Barlow-Bold.ttf", "Doc Heading", 700, FontStyle.NORMAL);
            builder.withProducer("SL Tours documents");
            builder.withHtmlContent(html(view), null);
            builder.toStream(out);
            builder.run();
        } catch (IOException e) {
            throw new UncheckedIOException("The document could not be generated", e);
        }
        return out.toByteArray();
    }

    private static void font(PdfRendererBuilder builder, String file, String family, int weight, FontStyle style) {
        builder.useFont(() -> PdfRenderer.class.getResourceAsStream(FONTS + file), family, weight, style, true);
    }

    String html(DocumentView v) {
        StringBuilder h = new StringBuilder(16_000);
        h.append("<!DOCTYPE html><html lang=\"en-ZA\"><head><meta charset=\"UTF-8\"/><title>").append(esc(v.filenameBase())).append("</title>")
            .append("<meta name=\"author\" content=\"").append(esc(v.company().name())).append("\"/>")
            .append("<meta name=\"subject\" content=\"").append(esc(v.title())).append("\"/>")
            .append("<style>").append(CSS).append("</style></head><body>");
        footer(h, v.company());
        if (v.watermark() != null) h.append("<div class=\"watermark\">").append(esc(v.watermark())).append("</div>");
        header(h, v);
        // The template's label column fits "INVOICE NUMBER"; longer labels such as "BOOKING REFERENCE" get a wider column.
        boolean wide = v.info().stream().anyMatch(r -> r.label().length() > 14);
        h.append("<table class=\"info\" style=\"width:").append(wide ? "114.5mm" : "107.4mm").append("\"><colgroup><col style=\"width:")
            .append(wide ? "52mm" : "44.9mm").append("\"/><col/></colgroup>");
        for (Row r : v.info()) h.append("<tr><td>").append(esc(r.label())).append("</td><td>").append(esc(r.value())).append("</td></tr>");
        h.append("</table>");
        services(h, v);
        if (v.notesHeading() != null) {
            h.append("<div class=\"keep\"><h2>").append(esc(v.notesHeading())).append("</h2>");
            for (String n : v.notes()) h.append("<p class=\"note\">").append(esc(n)).append("</p>");
            h.append("</div>");
        }
        if (v.bankingHeading() != null) {
            h.append("<div class=\"keep\"><h2>").append(esc(v.bankingHeading())).append("</h2><table class=\"bank\"><colgroup><col style=\"width:52.4mm\"/><col/></colgroup>");
            for (Row r : v.banking()) h.append("<tr><td>").append(esc(r.label())).append("</td><td>").append(esc(r.value())).append("</td></tr>");
            h.append("</table></div>");
        }
        if (v.termsHeading() != null) {
            h.append("<div class=\"keep\"><h2>").append(esc(v.termsHeading())).append("</h2><table class=\"terms\">");
            for (String t : v.terms()) h.append("<tr><td class=\"dash\">-</td><td>").append(esc(t)).append("</td></tr>");
            h.append("</table></div>");
        }
        return h.append("</body></html>").toString();
    }

    private void header(StringBuilder h, DocumentView v) {
        Company c = v.company();
        Logo logo = c.logoRef() == null ? null : logos.resolve(c.logoRef());
        h.append("<div class=\"masthead\">");
        if (logo != null) h.append("<img class=\"logo\" alt=\"").append(esc(c.name())).append(" logo\" src=\"data:").append(logo.contentType())
            .append(";base64,").append(Base64.getEncoder().encodeToString(logo.bytes())).append("\"/>");
        if (c.tagline() != null && !c.tagline().isBlank()) h.append("<p class=\"tagline\">").append(tagline(c.tagline())).append("</p>");
        h.append("</div><h1>").append(esc(v.title())).append("</h1>");
        if (v.watermark() != null) h.append("<p class=\"status\">").append(esc(v.watermark())).append(" – not valid for payment</p>");
    }

    /** The template colours the brand words: "SL" in orange and "Tours" in navy. */
    static String tagline(String text) {
        String t = text.trim();
        if (t.startsWith("SL Tours")) return "<span class=\"brand-sl\">SL</span> <span class=\"brand-tours\">Tours</span>" + esc(t.substring(8));
        return esc(t);
    }

    private void services(StringBuilder h, DocumentView v) {
        h.append("<table class=\"services\"><colgroup><col style=\"width:99.9mm\"/><col/></colgroup>")
            .append("<thead><tr><th>DESCRIPTION</th><th>").append(esc(v.costHeader())).append("</th></tr></thead><tbody>");
        List<Item> items = v.items();
        for (int i = 0; i < items.size(); i++) {
            // The last service stays on the same page as the totals, so a total is never printed on its own.
            if (i == items.size() - 1) h.append("</tbody><tbody class=\"totals\">");
            Item item = items.get(i);
            h.append("<tr class=\"item").append(i == 0 ? " first" : "").append("\"><td>");
            if (item.title() != null) h.append("<p class=\"title\">").append(esc(item.title())).append("</p>");
            for (Piece p : item.pieces()) piece(h, p);
            h.append("</td><td>").append(item.amount() == null ? "" : esc(item.amount())).append("</td></tr>");
        }
        h.append("<tr class=\"filler\"><td></td><td></td></tr>");
        for (int i = 0; i < v.blankRows(); i++) h.append("<tr class=\"sum\"><td>&#160;</td><td>&#160;</td></tr>");
        for (TotalRow t : v.totals())
            h.append("<tr class=\"sum").append(t.grand() ? " grand" : "").append("\"").append("><td>").append(esc(t.label())).append("</td><td>")
                .append(esc(t.amount())).append("</td></tr>");
        h.append("</tbody></table>");
    }

    private static void piece(StringBuilder h, Piece p) {
        switch (p.kind()) {
            case GAP -> h.append("<p class=\"gap\">&#160;</p>");
            case TEXT -> h.append("<p>").append(esc(p.text())).append("</p>");
            case LABELLED -> h.append("<p><b>").append(esc(p.label())).append(":</b> ").append(esc(p.text())).append("</p>");
            case BULLETS -> {
                if (p.label() != null) h.append("<p><b>").append(esc(p.label())).append(":</b></p>");
                h.append("<ul>");
                for (String b : p.bullets()) h.append("<li>").append(esc(b)).append("</li>");
                h.append("</ul>");
            }
        }
    }

    private static void footer(StringBuilder h, Company c) {
        h.append("<div class=\"footer\"><div class=\"strip\"><div class=\"orange\"></div><div class=\"blue\"></div></div>")
            .append("<p class=\"name\">").append(esc(c.name())).append("</p><p>");
        boolean first = true;
        if (c.phone() != null && !c.phone().isBlank()) { h.append("<b>Mobile:</b> ").append(esc(c.phone())); first = false; }
        if (c.whatsapp() != null && !c.whatsapp().isBlank() && !c.whatsapp().equals(c.phone())) {
            h.append(first ? "" : " ").append("<b>WhatsApp:</b> ").append(esc(c.whatsapp())); first = false;
        }
        if (c.email() != null && !c.email().isBlank()) h.append(first ? "" : " ").append("<b>Email:</b> ").append(esc(c.email()));
        h.append("</p>");
        if (c.website() != null && !c.website().isBlank()) h.append("<p><b>Website:</b> ").append(esc(c.website())).append("</p>");
        h.append("<p><b>Address:</b> ").append(esc(c.address())).append("</p></div>");
    }

    static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '&' -> b.append("&amp;");
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '"' -> b.append("&quot;");
                case '\'' -> b.append("&#39;");
                default -> { if (ch >= 0x20 || ch == '\t') b.append(ch); }
            }
        }
        return b.toString();
    }

    /** Measurements follow the Word template (A4, 1-inch side margins, 0.5 pt borders, 12 pt Arial). */
    private static final String CSS = """
        @page { size: 210mm 297mm; margin: 12mm 0 31mm 0;
          @bottom-center { content: element(footer); vertical-align: top; } }
        body { margin: 0; padding: 0 25.4mm; font-family: 'Doc Sans'; font-size: 12pt; line-height: 1.15; color: #000; }
        p { margin: 0; }
        .footer { position: running(footer); width: 210mm; text-align: center; font-size: 9pt; line-height: 1.21; color: #404040; }
        .footer .strip { height: 2.95mm; margin: 4.3mm 0 4.8mm 0; }
        .footer .orange { float: left; width: 70.3%; height: 2.95mm; background: #f25a2b; }
        .footer .blue { float: left; width: 29.7%; height: 2.95mm; background: #293990; }
        .footer b, .footer .name { font-weight: bold; color: #ff3300; }
        .watermark { position: fixed; top: 105mm; left: 0; width: 210mm; text-align: center; font-family: 'Doc Heading';
          font-size: 110pt; color: #e8e8e8; transform: rotate(-35deg); z-index: -1; }
        .masthead { text-align: center; }
        .logo { width: 30mm; height: 29.4mm; }
        .tagline { margin-top: 5.6mm; font-style: italic; font-size: 11pt; color: #404040; }
        .brand-sl { color: #ff3300; }
        .brand-tours { color: #1f3864; }
        h1 { font-family: 'Doc Heading'; font-weight: bold; font-size: 11pt; color: #ff6600; margin: 3.8mm 0 3.6mm 0; letter-spacing: 0.1pt; }
        h2 { font-size: 12pt; font-weight: bold; margin: 8.3mm 0 3.2mm 0; }
        table { border-collapse: collapse; }
        td, th { border: 0.5pt solid #000; padding: 0 1.9mm; vertical-align: top; text-align: left; font-weight: normal; }
        .status { margin: -1.5mm 0 3mm 0; font-weight: bold; font-size: 11pt; color: #c00000; }
        table.services { width: 149.9mm; margin-top: 8.2mm; -fs-table-paginate: paginate; }
        table.services th { height: 8.4mm; }
        table.services tr.item td { border-top: none; border-bottom: none; padding-top: 1mm; padding-bottom: 1mm; }
        table.services tr.item.first td { padding-top: 0.4mm; }
        table.services tr.item td + td { padding-top: 1mm; }
        table.services tr.filler td { height: 14.3mm; border-top: none; }
        table.services tr.item { page-break-inside: auto; }
        table.services tbody.totals { page-break-inside: avoid; }
        table.services tbody.totals tr.sum td { height: 5mm; }
        table.services tr.sum.grand td + td { font-weight: bold; }
        .title { font-weight: bold; }
        .gap { line-height: 1.15; }
        ul { margin: 0; padding: 0 0 0 12.4mm; }
        li { margin: 0; }
        table.bank { width: 139.9mm; }
        table.terms td { border: none; padding: 0; font-size: 10pt; text-transform: uppercase; }
        table.terms td.dash { width: 6.3mm; padding-left: 6.3mm; }
        p.note { margin-bottom: 1mm; }
        .keep { page-break-inside: avoid; }
        """;
}
