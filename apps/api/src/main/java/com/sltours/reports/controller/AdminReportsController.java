package com.sltours.reports.controller;

import com.sltours.reports.dto.ReportDtos.WorkQueues;
import com.sltours.reports.service.CsvExportService;
import com.sltours.reports.service.WorkQueueService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminReportsController {
    private final WorkQueueService queues;
    private final CsvExportService exports;
    public AdminReportsController(WorkQueueService queues, CsvExportService exports) { this.queues = queues; this.exports = exports; }

    @GetMapping("/work-queues") WorkQueues queues() { return queues.queues(); }

    /** Personal and financial data leaves the system here, so exporting is limited to administrators and audited. */
    @GetMapping("/exports/{dataset}") @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<byte[]> export(@PathVariable String dataset, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        CsvExportService.Dataset d;
        try { d = CsvExportService.Dataset.valueOf(dataset.toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Choose customers, enquiries, bookings, invoices or payments"); }
        var file = exports.export(d, from, to);
        return ResponseEntity.ok().contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.filename()).build().toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store").header("X-Export-Rows", String.valueOf(file.rows())).body(file.bytes());
    }
}
