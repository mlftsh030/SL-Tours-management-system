package com.sltours.reports.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CsvExportServiceTest {
    @Test void formulasAreNeutralisedButPhoneNumbersAndAmountsStayReadable() {
        assertThat(CsvExportService.cell("=HYPERLINK(\"http://x\")")).isEqualTo("\"'=HYPERLINK(\"\"http://x\"\")\"");
        assertThat(CsvExportService.cell("+SUM(A1:A9)")).isEqualTo("\"'+SUM(A1:A9)\"");
        assertThat(CsvExportService.cell("@cmd")).isEqualTo("\"'@cmd\"");
        assertThat(CsvExportService.cell("-2+3+cmd|' /C calc'!A0")).startsWith("\"'-");
        assertThat(CsvExportService.cell("+27 82 555 1234")).isEqualTo("\"+27 82 555 1234\"");
        assertThat(CsvExportService.cell("-1500")).isEqualTo("\"-1500\"");
        assertThat(CsvExportService.cell("Thandi \"T\" M")).isEqualTo("\"Thandi \"\"T\"\" M\"");
    }
}
