package com.sltours.billing.render;
import com.sltours.billing.render.DocumentView.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.zip.*;

/**
 * Writes the same {@link DocumentView} as an editable Word document (.docx, Office Open XML) laid out like the SL Tours
 * template: real tables, a bulleted drop-off list, the logo, and the coloured strip and contact block in the footer.
 * Edits made in Word stay in that file; they do not change the document stored in the system.
 */
public final class DocxRenderer {
    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final long EMU_PER_MM = 36_000;
    private final PdfRenderer.LogoSource logos;
    public DocxRenderer(PdfRenderer.LogoSource logos) { this.logos = logos; }

    public byte[] render(DocumentView v) {
        PdfRenderer.Logo logo = v.company().logoRef() == null ? null : logos.resolve(v.company().logoRef());
        String logoExt = logo == null ? null : logo.contentType().equals("image/png") ? "png" : logo.contentType().equals("image/webp") ? "webp" : "jpeg";
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            put(zip, "[Content_Types].xml", contentTypes());
            put(zip, "_rels/.rels", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
                <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
                </Relationships>""");
            put(zip, "docProps/core.xml", core(v));
            put(zip, "docProps/app.xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties"><Application>SL Tours documents</Application></Properties>""");
            put(zip, "word/_rels/document.xml.rels", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rIdStyles" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                <Relationship Id="rIdNumbering" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering" Target="numbering.xml"/>
                <Relationship Id="rIdSettings" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/settings" Target="settings.xml"/>
                <Relationship Id="rIdFooter" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer" Target="footer1.xml"/>
                """ + (logo == null ? "" : "<Relationship Id=\"rIdLogo\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/logo." + logoExt + "\"/>\n")
                + "</Relationships>");
            put(zip, "word/_rels/footer1.xml.rels", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rIdStrip" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/strip.png"/>
                </Relationships>""");
            put(zip, "word/styles.xml", STYLES);
            put(zip, "word/numbering.xml", NUMBERING);
            put(zip, "word/settings.xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:settings xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:defaultTabStop w:val="720"/><w:compat><w:compatSetting w:name="compatibilityMode" w:uri="http://schemas.microsoft.com/office/word" w:val="15"/></w:compat></w:settings>""");
            put(zip, "word/footer1.xml", footer(v.company()));
            put(zip, "word/document.xml", document(v, logo != null));
            if (logo != null) putBytes(zip, "word/media/logo." + logoExt, logo.bytes());
            try (InputStream strip = DocxRenderer.class.getResourceAsStream("/billing/footer-strip.png")) {
                putBytes(zip, "word/media/strip.png", strip.readAllBytes());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("The Word document could not be generated", e);
        }
        return bytes.toByteArray();
    }

    private String document(DocumentView v, boolean hasLogo) {
        StringBuilder b = new StringBuilder(20_000);
        b.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:document xmlns:w=\"").append(W).append("\" xmlns:r=\"").append(R)
            .append("\" xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\"")
            .append(" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\"")
            .append(" xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><w:body>");
        if (v.watermark() != null)
            b.append(para("<w:jc w:val=\"center\"/>", run(v.watermark() + " – not valid for payment", "<w:b/><w:color w:val=\"C00000\"/><w:sz w:val=\"28\"/>")));
        if (hasLogo) b.append("<w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr><w:r>").append(inlineImage("rIdLogo", 30, 29.4, 1, "Logo")).append("</w:r></w:p>");
        String tagline = v.company().tagline();
        if (tagline != null && !tagline.isBlank()) {
            String pPr = "<w:spacing w:before=\"320\"/><w:jc w:val=\"center\"/>";
            String it = "<w:i/><w:sz w:val=\"22\"/>";
            String t = tagline.trim();
            if (t.startsWith("SL Tours")) b.append(para(pPr, run("SL", it + "<w:color w:val=\"FF3300\"/>") + run(" ", it)
                + run("Tours", it + "<w:color w:val=\"1F3864\"/>") + run(t.substring(8), it + "<w:color w:val=\"404040\"/>")));
            else b.append(para(pPr, run(t, it + "<w:color w:val=\"404040\"/>")));
        }
        b.append(para("<w:spacing w:before=\"215\" w:after=\"200\"/>", run(v.title(),
            "<w:rFonts w:ascii=\"Barlow\" w:hAnsi=\"Barlow\"/><w:b/><w:color w:val=\"FF6600\"/><w:sz w:val=\"22\"/>")));
        boolean wide = v.info().stream().anyMatch(r -> r.label().length() > 14);
        table(b, wide ? new int[]{2948, 3544} : new int[]{2547, 3544}, v.info());
        b.append(para("<w:spacing w:after=\"220\"/>", ""));
        services(b, v);
        if (v.notesHeading() != null) {
            heading(b, v.notesHeading());
            for (String n : v.notes()) b.append(para("<w:spacing w:after=\"60\"/>", run(n, "")));
        }
        if (v.bankingHeading() != null) {
            heading(b, v.bankingHeading());
            table(b, new int[]{2972, 4961}, v.banking());
        }
        if (v.termsHeading() != null) {
            heading(b, v.termsHeading());
            for (String t : v.terms())
                b.append(para("<w:tabs><w:tab w:val=\"left\" w:pos=\"714\"/></w:tabs><w:ind w:left=\"714\" w:hanging=\"357\"/>",
                    run("-", "<w:sz w:val=\"20\"/>") + "<w:r><w:tab/></w:r>" + run(t, "<w:caps/><w:sz w:val=\"20\"/>")));
        }
        b.append("<w:sectPr><w:footerReference w:type=\"default\" r:id=\"rIdFooter\"/><w:pgSz w:w=\"11906\" w:h=\"16838\"/>")
            .append("<w:pgMar w:top=\"680\" w:right=\"1440\" w:bottom=\"1757\" w:left=\"1440\" w:header=\"340\" w:footer=\"397\" w:gutter=\"0\"/></w:sectPr>")
            .append("</w:body></w:document>");
        return b.toString();
    }

    private void services(StringBuilder b, DocumentView v) {
        b.append("<w:tbl><w:tblPr><w:tblW w:w=\"8500\" w:type=\"dxa\"/>").append(BORDERS).append("<w:tblLayout w:type=\"fixed\"/>")
            .append("<w:tblCellMar><w:left w:w=\"108\" w:type=\"dxa\"/><w:right w:w=\"108\" w:type=\"dxa\"/></w:tblCellMar></w:tblPr>")
            .append("<w:tblGrid><w:gridCol w:w=\"5665\"/><w:gridCol w:w=\"2835\"/></w:tblGrid>");
        b.append("<w:tr><w:trPr><w:tblHeader/><w:trHeight w:val=\"476\"/></w:trPr>")
            .append(cell(5665, "", para("", run("DESCRIPTION", "")))).append(cell(2835, "", para("", run(v.costHeader(), ""))))
            .append("</w:tr>");
        List<Item> items = v.items();
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            String borders = "<w:tcBorders>" + (i == 0 ? "" : "<w:top w:val=\"nil\"/>") + "<w:bottom w:val=\"nil\"/></w:tcBorders>";
            StringBuilder desc = new StringBuilder();
            String spacing = i == 0 ? "" : "<w:spacing w:before=\"120\"/>";
            if (item.title() != null) desc.append(para(spacing, run(item.title(), "<w:b/>")));
            else if (!spacing.isEmpty()) desc.append(para(spacing, ""));
            for (Piece p : item.pieces()) desc.append(piece(p));
            if (desc.isEmpty()) desc.append(para("", ""));
            b.append("<w:tr>").append(cell(5665, borders, desc.toString()))
                .append(cell(2835, borders, para(spacing, item.amount() == null ? "" : run(item.amount(), "")))).append("</w:tr>");
        }
        String filler = "<w:tcBorders><w:top w:val=\"nil\"/></w:tcBorders>";
        b.append("<w:tr><w:trPr><w:trHeight w:val=\"811\"/></w:trPr>").append(cell(5665, filler, para("", ""))).append(cell(2835, filler, para("", ""))).append("</w:tr>");
        for (int i = 0; i < v.blankRows(); i++) b.append("<w:tr><w:trPr><w:cantSplit/></w:trPr>").append(cell(5665, "", para("", ""))).append(cell(2835, "", para("", ""))).append("</w:tr>");
        for (TotalRow t : v.totals())
            b.append("<w:tr><w:trPr><w:cantSplit/></w:trPr>").append(cell(5665, "", para("<w:keepNext/>", run(t.label(), ""))))
                .append(cell(2835, "", para("<w:keepNext/>", run(t.amount(), t.grand() ? "<w:b/>" : "")))).append("</w:tr>");
        b.append("</w:tbl>");
    }

    private static String piece(Piece p) {
        return switch (p.kind()) {
            case GAP -> para("", "");
            case TEXT -> para("", run(p.text(), ""));
            case LABELLED -> para("", run(p.label() + ":", "<w:b/>") + run(" " + p.text(), ""));
            case BULLETS -> {
                StringBuilder s = new StringBuilder();
                if (p.label() != null) s.append(para("", run(p.label() + ":", "<w:b/>")));
                for (String item : p.bullets()) s.append(para("<w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"1\"/></w:numPr>", run(item, "")));
                yield s.toString();
            }
        };
    }

    private static void table(StringBuilder b, int[] widths, List<Row> rows) {
        int total = widths[0] + widths[1];
        b.append("<w:tbl><w:tblPr><w:tblW w:w=\"").append(total).append("\" w:type=\"dxa\"/>").append(BORDERS)
            .append("<w:tblLayout w:type=\"fixed\"/><w:tblCellMar><w:left w:w=\"108\" w:type=\"dxa\"/><w:right w:w=\"108\" w:type=\"dxa\"/></w:tblCellMar></w:tblPr>")
            .append("<w:tblGrid><w:gridCol w:w=\"").append(widths[0]).append("\"/><w:gridCol w:w=\"").append(widths[1]).append("\"/></w:tblGrid>");
        for (Row r : rows)
            b.append("<w:tr><w:trPr><w:cantSplit/></w:trPr>").append(cell(widths[0], "", para("", run(r.label(), ""))))
                .append(cell(widths[1], "", para("", run(r.value(), "")))).append("</w:tr>");
        b.append("</w:tbl>");
    }

    private static void heading(StringBuilder b, String text) {
        b.append(para("<w:keepNext/><w:spacing w:before=\"470\" w:after=\"180\"/>", run(text, "<w:b/>")));
    }

    private String footer(Company c) {
        StringBuilder b = new StringBuilder();
        b.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:ftr xmlns:w=\"").append(W).append("\" xmlns:r=\"").append(R)
            .append("\" xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\"")
            .append(" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\"")
            .append(" xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">");
        // The strip runs edge to edge, so it is anchored to the page rather than placed inside the margins.
        b.append("<w:p><w:pPr><w:spacing w:after=\"300\"/></w:pPr><w:r>").append(anchoredStrip()).append("</w:r></w:p>");
        String red = "<w:b/><w:color w:val=\"FF3300\"/><w:sz w:val=\"18\"/>", grey = "<w:color w:val=\"404040\"/><w:sz w:val=\"18\"/>";
        String center = "<w:spacing w:after=\"0\" w:line=\"250\" w:lineRule=\"auto\"/><w:jc w:val=\"center\"/>";
        b.append(para(center, run(c.name(), red)));
        StringBuilder contact = new StringBuilder();
        if (c.phone() != null && !c.phone().isBlank()) contact.append(run("Mobile:", red)).append(run(" " + c.phone(), grey));
        if (c.whatsapp() != null && !c.whatsapp().isBlank() && !c.whatsapp().equals(c.phone()))
            contact.append(run(contact.isEmpty() ? "WhatsApp:" : " WhatsApp:", red)).append(run(" " + c.whatsapp(), grey));
        if (c.email() != null && !c.email().isBlank()) contact.append(run(contact.isEmpty() ? "Email:" : " Email:", red)).append(run(" " + c.email(), grey));
        b.append(para(center, contact.toString()));
        if (c.website() != null && !c.website().isBlank()) b.append(para(center, run("Website:", red) + run(" " + c.website(), grey)));
        b.append(para(center, run("Address:", red) + run(" " + c.address(), grey)));
        return b.append("</w:ftr>").toString();
    }

    private static String inlineImage(String rel, double widthMm, double heightMm, int id, String name) {
        long cx = Math.round(widthMm * EMU_PER_MM), cy = Math.round(heightMm * EMU_PER_MM);
        return "<w:drawing><wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\"><wp:extent cx=\"" + cx + "\" cy=\"" + cy + "\"/>"
            + "<wp:docPr id=\"" + id + "\" name=\"" + name + "\"/>" + graphic(rel, cx, cy, id, name) + "</wp:inline></w:drawing>";
    }

    private static String anchoredStrip() {
        long cx = 210 * EMU_PER_MM, cy = Math.round(2.95 * EMU_PER_MM);
        return "<w:drawing><wp:anchor distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\" simplePos=\"0\" relativeHeight=\"1\" behindDoc=\"0\" locked=\"1\""
            + " layoutInCell=\"1\" allowOverlap=\"1\"><wp:simplePos x=\"0\" y=\"0\"/>"
            + "<wp:positionH relativeFrom=\"page\"><wp:posOffset>0</wp:posOffset></wp:positionH>"
            + "<wp:positionV relativeFrom=\"paragraph\"><wp:posOffset>0</wp:posOffset></wp:positionV>"
            + "<wp:extent cx=\"" + cx + "\" cy=\"" + cy + "\"/><wp:effectExtent l=\"0\" t=\"0\" r=\"0\" b=\"0\"/><wp:wrapTopAndBottom/>"
            + "<wp:docPr id=\"2\" name=\"Footer strip\"/>" + graphic("rIdStrip", cx, cy, 2, "Footer strip") + "</wp:anchor></w:drawing>";
    }

    private static String graphic(String rel, long cx, long cy, int id, String name) {
        return "<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><pic:pic>"
            + "<pic:nvPicPr><pic:cNvPr id=\"" + id + "\" name=\"" + name + "\"/><pic:cNvPicPr/></pic:nvPicPr>"
            + "<pic:blipFill><a:blip r:embed=\"" + rel + "\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>"
            + "<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"" + cx + "\" cy=\"" + cy + "\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr>"
            + "</pic:pic></a:graphicData></a:graphic>";
    }

    private static String cell(int width, String tcPr, String content) {
        return "<w:tc><w:tcPr><w:tcW w:w=\"" + width + "\" w:type=\"dxa\"/>" + tcPr + "</w:tcPr>" + content + "</w:tc>";
    }
    private static String para(String pPr, String runs) {
        return "<w:p>" + (pPr.isEmpty() ? "" : "<w:pPr>" + pPr + "</w:pPr>") + runs + "</w:p>";
    }
    private static String run(String text, String rPr) {
        return "<w:r>" + (rPr.isEmpty() ? "" : "<w:rPr>" + rPr + "</w:rPr>") + "<w:t xml:space=\"preserve\">" + PdfRenderer.esc(text) + "</w:t></w:r>";
    }

    private static String core(DocumentView v) {
        String now = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\""
            + " xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:dcterms=\"http://purl.org/dc/terms/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"
            + "<dc:title>" + PdfRenderer.esc(v.filenameBase()) + "</dc:title><dc:subject>" + PdfRenderer.esc(v.title()) + "</dc:subject>"
            + "<dc:creator>" + PdfRenderer.esc(v.company().name()) + "</dc:creator>"
            + "<dcterms:created xsi:type=\"dcterms:W3CDTF\">" + now + "</dcterms:created></cp:coreProperties>";
    }

    private static String contentTypes() {
        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
            <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
            <Default Extension="xml" ContentType="application/xml"/>
            <Default Extension="png" ContentType="image/png"/>
            <Default Extension="jpeg" ContentType="image/jpeg"/>
            <Default Extension="webp" ContentType="image/webp"/>
            <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
            <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
            <Override PartName="/word/numbering.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"/>
            <Override PartName="/word/settings.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml"/>
            <Override PartName="/word/footer1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"/>
            <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
            <Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>
            </Types>""";
    }

    private static void put(ZipOutputStream zip, String name, String xml) throws IOException {
        putBytes(zip, name, xml.strip().getBytes(StandardCharsets.UTF_8));
    }
    private static void putBytes(ZipOutputStream zip, String name, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0);
        zip.putNextEntry(entry);
        zip.write(data);
        zip.closeEntry();
    }

    private static final String BORDERS = "<w:tblBorders><w:top w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>"
        + "<w:left w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/><w:bottom w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>"
        + "<w:right w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/><w:insideH w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>"
        + "<w:insideV w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/></w:tblBorders>";

    private static final String STYLES = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
        <w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Arial" w:hAnsi="Arial" w:eastAsia="Arial" w:cs="Arial"/><w:sz w:val="24"/><w:szCs w:val="24"/><w:lang w:val="en-ZA"/></w:rPr></w:rPrDefault>
        <w:pPrDefault><w:pPr><w:spacing w:after="0" w:line="240" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults>
        <w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:qFormat/></w:style>
        <w:style w:type="table" w:default="1" w:styleId="TableNormal"><w:name w:val="Normal Table"/><w:tblPr><w:tblInd w:w="0" w:type="dxa"/>
        <w:tblCellMar><w:top w:w="0" w:type="dxa"/><w:left w:w="108" w:type="dxa"/><w:bottom w:w="0" w:type="dxa"/><w:right w:w="108" w:type="dxa"/></w:tblCellMar></w:tblPr></w:style>
        </w:styles>""";

    private static final String NUMBERING = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <w:numbering xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
        <w:abstractNum w:abstractNumId="0"><w:multiLevelType w:val="singleLevel"/>
        <w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="bullet"/><w:lvlText w:val="•"/><w:lvlJc w:val="left"/>
        <w:pPr><w:ind w:left="703" w:hanging="283"/></w:pPr><w:rPr><w:rFonts w:ascii="Arial" w:hAnsi="Arial"/></w:rPr></w:lvl></w:abstractNum>
        <w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num>
        </w:numbering>""";
}
