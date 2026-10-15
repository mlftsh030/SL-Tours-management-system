package com.sltours.billing;
import com.sltours.billing.model.BillingTypes.*;
import com.sltours.billing.render.*;
import com.sltours.billing.service.BillingCalculator;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.*;
import org.junit.jupiter.api.Test;
import org.w3c.dom.NodeList;
import static org.assertj.core.api.Assertions.*;

/**
 * Renders the reference invoice and other examples, checks layout properties of the PDFs and that the Word files carry
 * the same content. The files are written to target/billing-examples for visual comparison with INV-100099.pdf.
 */
class DocumentRenderingTest {
    static final Path OUT = Path.of("target/billing-examples");
    final PdfRenderer pdf = new PdfRenderer(BillingFixtures.bundledLogo());
    final DocxRenderer docx = new DocxRenderer(BillingFixtures.bundledLogo());

    static String text(byte[] bytes) throws IOException {
        try (PDDocument d = Loader.loadPDF(bytes)) { PDFTextStripper s = new PDFTextStripper(); s.setSortByPosition(true); return s.getText(d); }
    }

    byte[] write(String name, DocumentView view) throws IOException {
        Files.createDirectories(OUT);
        byte[] p = pdf.render(view), w = docx.render(view);
        Files.write(OUT.resolve(name + ".pdf"), p);
        Files.write(OUT.resolve(name + ".docx"), w);
        return p;
    }

    @Test void theReferenceInvoiceFitsOnePageWithSelectableTextInEmbeddedFonts() throws Exception {
        byte[] bytes = write("INV-100099", BillingFixtures.view(BillingFixtures.youngLife(), 100099, null));
        try (PDDocument d = Loader.loadPDF(bytes)) {
            assertThat(d.getNumberOfPages()).isEqualTo(1);
            assertThat(d.getPage(0).getMediaBox().getWidth()).isCloseTo(595.3f, within(0.5f));   // A4 portrait
            assertThat(d.getPage(0).getMediaBox().getHeight()).isCloseTo(841.9f, within(0.5f));
            assertThat(d.getDocumentInformation().getTitle()).isEqualTo("INV-100099");
            for (var name : d.getPage(0).getResources().getFontNames()) {
                PDFont font = d.getPage(0).getResources().getFont(name);
                assertThat(font.isEmbedded()).as(font.getName()).isTrue();
            }
        }
        String text = text(bytes);
        assertThat(text).contains("INVOICE", "DATE 14-07-2026", "CLIENT Sample Client", "INVOICE NUMBER 100099", "DESCRIPTION COST - R", "Transfer",
            "Pick up: Cape Town international airport", "Drop off: Nieuwe Sion Estate", "Pick up: Nieuwe Sion Estate", "Kenilworth", "Gugulethu",
            "Macasgavi", "Crossroads", "Mfuleni", "GRAND TOTAL: R2 850", "BANKING DETAILS", "ACCOUNT NAME SL TOURS (PTY) LTD", "BANK FNB SA",
            "ACCOUNT NUMBER 00000000000", "TERMS AND CONDITIONS", "SUBMIT PROOF OF PAYMENT VIA WHATSAPP OR EMAIL.", "SL Tours (Pty) Ltd",
            "Mobile: +27 67 012 7776", "Email: tours.st@outlook.com", "Address: 100 Example Street Cape Town 8001",
            "offers excellent opportunities to explore South Africa.");
        assertThat(text).doesNotContain("Dropp", "Tax Invoice", "VAT");
    }

    @Test void longDocumentsContinueAcrossPagesWithoutOverlapsOrADetachedTotal() throws Exception {
        byte[] bytes = write("INV-100100-multi-page", BillingFixtures.view(BillingFixtures.longGroupTrip(), 100100, null));
        try (PDDocument d = Loader.loadPDF(bytes)) {
            int pages = d.getNumberOfPages();
            assertThat(pages).isGreaterThan(2);
            for (int p = 1; p <= pages; p++) {
                String page = pageText(d, p);
                assertThat(page).as("page " + p).contains("SL Tours (Pty) Ltd", "Address: 100 Example");   // footer on every page
                assertThat(overlaps(d, p)).as("overlapping glyphs on page " + p).isEmpty();
            }
            String totalPage = "";
            for (int p = 1; p <= pages; p++) if (pageText(d, p).contains("GRAND TOTAL")) totalPage = pageText(d, p);
            assertThat(totalPage).contains("Less: deposit already paid", "SUBTOTAL: 22 300", "GROUP DISCOUNT (5%): (1 115)", "R21 185");
            assertThat(pageText(d, 2)).contains("DESCRIPTION");   // the table header repeats
        }
        String all = text(bytes);
        for (String place : List.of("Kenilworth", "Mitchells Plain", "Durbanville")) assertThat(all).contains(place);
        assertThat(all).contains("2 × R1 950 per vehicle", "(5 000)", "PASSENGERS 26", "SERVICE DATES 19-05-2026 to 27-05-2026",
            "THE REMAINING BALANCE MUST BE PAID BEFORE THE ARRIVAL DATE.");
    }

    @Test void draftsAndVoidedDocumentsAreMarkedOnEveryCopy() throws Exception {
        assertThat(text(write("INV-DRAFT-example", BillingFixtures.view(BillingFixtures.youngLife(), 100099, "DRAFT")))).contains("DRAFT – not valid for payment");
        DocumentView voided = BillingFixtures.view(BillingFixtures.youngLife(), 100099, "VOID");
        assertThat(text(pdf.render(voided))).contains("VOID – not valid for payment");
        assertThat(docxText(docx.render(voided))).contains("VOID – not valid for payment");
    }

    @Test void quotationsAndReceiptsUseTheSameBranding() throws Exception {
        DocumentContent c = BillingFixtures.youngLife();
        DocumentContent quote = new DocumentContent(c.customer(), LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 24), LocalDate.of(2026, 7, 20), null, null,
            "ZAR", 14, "Toyota Quantum", null, c.lines(), null, false, null, null, Template.DETAILED);
        DocumentView q = DocumentViews.document(Kind.QUOTATION, 1001L, BillingFixtures.FIXTURE_ID, null, quote, BillingCalculator.compute(quote, null),
            BillingFixtures.company(), BillingFixtures.banking(), List.of("Valid for 14 days."));
        String quoteText = text(write("QUO-1001", q));
        assertThat(quoteText).contains("QUOTATION", "QUOTATION NUMBER 1001", "VALID UNTIL 24-07-2026", "1 × R2 850", "2 850", "GRAND TOTAL: R2 850",
            "SL Tours (Pty) Ltd");

        ReceiptSnapshot r = new ReceiptSnapshot(1001, LocalDate.of(2026, 7, 16), Kind.INVOICE, 100099, c.customer(), null, "ZAR", 100_000,
            LocalDate.of(2026, 7, 15), PaymentMethod.EFT, "YL deposit", 285_000, 100_000, 185_000, BillingFixtures.company(), "admin", OffsetDateTime.now());
        String receiptText = text(write("REC-1001", DocumentViews.receipt(r, null)));
        assertThat(receiptText).contains("PAYMENT RECEIPT", "RECEIPT NUMBER 1001", "INVOICE NUMBER 100099", "Reference: YL deposit", "1 000",
            "TOTAL PAID TO DATE: 1 000", "BALANCE OUTSTANDING: R1 850");
    }

    @Test void wordAndPdfCarryTheSameContent() throws Exception {
        for (DocumentContent c : List.of(BillingFixtures.youngLife(), BillingFixtures.longGroupTrip())) {
            DocumentView view = BillingFixtures.view(c, 100099, null);
            String pdfText = text(pdf.render(view)).replaceAll("\\s+", " ");
            String wordText = docxText(docx.render(view)).replaceAll("\\s+", " ");
            List<String> expected = new ArrayList<>();
            view.info().forEach(r -> { expected.add(r.label()); expected.add(r.value()); });
            view.items().forEach(i -> {
                if (i.title() != null) expected.add(i.title());
                if (i.amount() != null) expected.add(i.amount());
                i.pieces().forEach(p -> { if (p.text() != null) expected.add(p.text()); expected.addAll(p.bullets()); });
            });
            view.totals().forEach(t -> { expected.add(t.label()); expected.add(t.amount()); });
            view.banking().forEach(r -> { expected.add(r.label()); expected.add(r.value()); });
            expected.add(view.company().address());
            for (String s : expected) {
                assertThat(pdfText).as("PDF").contains(s.replaceAll("\\s+", " "));
                assertThat(wordText).as("Word").contains(s.replaceAll("\\s+", " "));
            }
            view.terms().forEach(t -> assertThat(wordText).contains(t));
        }
    }

    @Test void wordFilesAreWellFormedPackages() throws Exception {
        byte[] bytes = docx.render(BillingFixtures.view(BillingFixtures.longGroupTrip(), 100100, null));
        Map<String, byte[]> parts = unzip(bytes);
        assertThat(parts).containsKeys("[Content_Types].xml", "_rels/.rels", "word/document.xml", "word/styles.xml", "word/numbering.xml",
            "word/footer1.xml", "word/media/logo.jpeg", "word/media/strip.png", "docProps/core.xml");
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        for (var e : parts.entrySet()) if (e.getKey().endsWith(".xml") || e.getKey().endsWith(".rels"))
            assertThatCode(() -> factory.newDocumentBuilder().parse(new ByteArrayInputStream(e.getValue()))).as(e.getKey()).doesNotThrowAnyException();
    }

    static String docxText(byte[] bytes) throws Exception {
        Map<String, byte[]> parts = unzip(bytes);
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        StringBuilder b = new StringBuilder();
        for (String part : List.of("word/document.xml", "word/footer1.xml")) {
            var doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(parts.get(part)));
            NodeList paragraphs = doc.getElementsByTagNameNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "p");
            for (int i = 0; i < paragraphs.getLength(); i++) {
                NodeList runs = ((org.w3c.dom.Element) paragraphs.item(i)).getElementsByTagNameNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "t");
                for (int j = 0; j < runs.getLength(); j++) b.append(runs.item(j).getTextContent());
                b.append('\n');
            }
        }
        return b.toString();
    }

    static Map<String, byte[]> unzip(byte[] bytes) throws IOException {
        Map<String, byte[]> parts = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (var e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) parts.put(e.getName(), zip.readAllBytes());
        }
        return parts;
    }

    static String pageText(PDDocument d, int page) throws IOException {
        PDFTextStripper s = new PDFTextStripper();
        s.setSortByPosition(true);
        s.setStartPage(page); s.setEndPage(page);
        return s.getText(d);
    }

    /** Glyph boxes that intersect a glyph on another line by more than a quarter of the smaller box. */
    static List<String> overlaps(PDDocument d, int page) throws IOException {
        List<TextPosition> glyphs = new ArrayList<>();
        PDFTextStripper s = new PDFTextStripper() {
            @Override protected void writeString(String text, List<TextPosition> positions) { glyphs.addAll(positions); }
        };
        s.setStartPage(page); s.setEndPage(page);
        s.getText(d);
        List<String> found = new ArrayList<>();
        for (int i = 0; i < glyphs.size(); i++) for (int j = i + 1; j < glyphs.size(); j++) {
            TextPosition a = glyphs.get(i), b = glyphs.get(j);
            if (a.getUnicode().isBlank() || b.getUnicode().isBlank() || Math.abs(a.getYDirAdj() - b.getYDirAdj()) < 1) continue;
            float w = Math.min(a.getXDirAdj() + a.getWidthDirAdj(), b.getXDirAdj() + b.getWidthDirAdj()) - Math.max(a.getXDirAdj(), b.getXDirAdj());
            float h = Math.min(a.getYDirAdj(), b.getYDirAdj()) - Math.max(a.getYDirAdj() - a.getHeightDir(), b.getYDirAdj() - b.getHeightDir());
            float smaller = Math.min(a.getWidthDirAdj() * a.getHeightDir(), b.getWidthDirAdj() * b.getHeightDir());
            if (w > 0 && h > 0 && w * h > smaller * 0.25f) found.add(a.getUnicode() + "/" + b.getUnicode() + " at " + a.getXDirAdj() + "," + a.getYDirAdj());
        }
        return found;
    }

    static { System.setProperty("java.awt.headless", "true"); }
    static final java.nio.charset.Charset UTF8 = StandardCharsets.UTF_8;
}
