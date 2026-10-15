package com.sltours.reports.service;

import com.sltours.audit.AuditService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CSV exports for administrators. Each export is audited (who, which dataset, which dates, how many rows). Internal
 * staff notes, password data, tokens and file contents are never exported. Cells that a spreadsheet would treat as a
 * formula are prefixed with an apostrophe.
 */
@Service
public class CsvExportService {
    public enum Dataset { CUSTOMERS, ENQUIRIES, BOOKINGS, INVOICES, PAYMENTS }
    public record Export(String filename, byte[] bytes, int rows) {}

    private static final Map<Dataset, String> SQL = Map.of(
        Dataset.CUSTOMERS, """
            select c.id, c.name, c.email, c.phone, c.country, c.account_id is not null as has_login, c.demo_data, c.created_at, c.updated_at
            from customers c where c.merged_into_id is null and c.created_at::date between ? and ? order by c.created_at""",
        Dataset.ENQUIRIES, """
            select e.reference, e.created_at, e.status, e.enquiry_type, e.source, e.customer_id, e.customer_name, e.customer_email, e.customer_phone,
              t.title as tour, e.requested_date, e.passengers, b.reference as booking, e.demo_data
            from enquiries e left join tours t on t.id = e.tour_id left join bookings b on b.id = e.converted_booking_id
            where e.created_at::date between ? and ? order by e.created_at""",
        Dataset.BOOKINGS, """
            select b.reference, b.created_at, b.booking_type, coalesce(b.title, t.title, 'Private trip') as title, b.status, b.travel_date, b.guests,
              b.customer_id, b.customer_name, b.customer_email, b.customer_phone, b.total_amount_cents, b.amount_paid_cents,
              q.number as quotation, b.demo_data
            from bookings b left join tours t on t.id = b.tour_id left join billing_documents q on q.id = b.source_quotation_id
            where b.travel_date between ? and ? order by b.travel_date, b.reference""",
        Dataset.INVOICES, """
            select d.kind, d.number, d.status, d.issue_date, d.due_date, d.customer_id, d.customer_name, d.customer_email, d.booking_reference,
              d.currency, d.subtotal_cents, d.discount_cents, d.tax_cents, d.total_cents, d.paid_cents, d.total_cents - d.paid_cents as balance_cents,
              d.payment_status, d.void_reason
            from billing_documents d where d.status <> 'DRAFT' and d.issue_date between ? and ? order by d.kind, d.number""",
        Dataset.PAYMENTS, """
            select p.recorded_at, p.paid_on, d.number as invoice, d.customer_name, p.amount_cents, d.currency, p.method, p.reference, p.status,
              p.receipt_number, p.recorded_by, p.decided_by, p.decided_at, p.reject_reason, p.reversed_by, p.reversed_at, p.reverse_reason
            from billing_payments p join billing_documents d on d.id = p.document_id where p.paid_on between ? and ? order by p.paid_on, p.recorded_at""");

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    public CsvExportService(JdbcTemplate jdbc, AuditService audit) { this.jdbc = jdbc; this.audit = audit; }

    @Transactional
    public Export export(Dataset dataset, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) throw new IllegalArgumentException("The start date is after the end date");
        if (from.plusYears(5).isBefore(to)) throw new IllegalArgumentException("Export at most five years at a time");
        StringBuilder csv = new StringBuilder("﻿");   // BOM so spreadsheet programs read UTF-8
        int[] rows = {0};
        jdbc.query(SQL.get(dataset), rs -> {
            var meta = rs.getMetaData();
            if (rows[0] == 0) {
                for (int c = 1; c <= meta.getColumnCount(); c++) csv.append(c > 1 ? "," : "").append(cell(meta.getColumnLabel(c)));
                csv.append("\r\n");
            }
            for (int c = 1; c <= meta.getColumnCount(); c++) { Object v = rs.getObject(c); csv.append(c > 1 ? "," : "").append(cell(v == null ? "" : v.toString())); }
            csv.append("\r\n");
            rows[0]++;
        }, from, to);
        if (rows[0] == 0) csv.append("no rows\r\n");
        // Exports are reads, but who exported personal data is recorded.
        audit.record("DATA_EXPORTED", dataset.name(), from + " to " + to + ", " + rows[0] + " rows");
        String name = "sl-tours-" + dataset.name().toLowerCase(Locale.ROOT) + "-" + from + "-to-" + to + ".csv";
        return new Export(name, csv.toString().getBytes(StandardCharsets.UTF_8), rows[0]);
    }

    /** Quotes every cell and neutralises spreadsheet formulas (cells starting =, +, -, @, tab or carriage return). */
    static String cell(String value) {
        String v = value;
        // Plain numbers and phone numbers (+27 82 …, -1500) cannot run as formulas and stay as they are.
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0 && !v.matches("[+-]?[0-9 ().-]+")) v = "'" + v;
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
