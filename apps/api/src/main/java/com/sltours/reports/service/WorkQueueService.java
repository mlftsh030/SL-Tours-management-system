package com.sltours.reports.service;

import com.sltours.reports.dto.ReportDtos.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What staff need to act on, oldest first. Development sample rows are left out.
 * <ul>
 *   <li>Enquiries awaiting follow-up: new for more than 4 hours, in progress for more than 2 days, or waiting for the
 *       customer for more than 5 days.</li>
 *   <li>Bookings in the next 21 days, including unconfirmed requests, with no active vehicle/driver assignment.</li>
 *   <li>Issued invoices with a balance, overdue first.</li>
 *   <li>Recorded payments waiting for an administrator to confirm, and customer-uploaded proof not yet matched to one.</li>
 * </ul>
 */
@Service
public class WorkQueueService {
    private final JdbcTemplate jdbc;
    public WorkQueueService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public WorkQueues queues() {
        var followUps = jdbc.query("""
            select e.reference, e.customer_id, e.customer_name, e.status, e.enquiry_type, e.created_at, e.updated_at,
              floor(extract(epoch from now() - e.updated_at) / 3600)::bigint
            from enquiries e where not e.demo_data and (
              (e.status = 'NEW' and e.updated_at < now() - interval '4 hours') or
              (e.status = 'IN_PROGRESS' and e.updated_at < now() - interval '2 days') or
              (e.status = 'AWAITING_CUSTOMER' and e.updated_at < now() - interval '5 days'))
            order by e.updated_at limit 200""",
            (rs, i) -> new FollowUp(rs.getString(1), (UUID) rs.getObject(2), rs.getString(3), rs.getString(4), rs.getString(5),
                rs.getObject(6, OffsetDateTime.class), rs.getObject(7, OffsetDateTime.class), rs.getLong(8)));
        var unassigned = jdbc.query("""
            select b.reference, b.customer_id, b.customer_name, coalesce(b.title, t.title, 'Private trip'), b.travel_date, b.guests, b.status
            from bookings b left join tours t on t.id = b.tour_id
            where not b.demo_data and b.travel_date between current_date and current_date + 21
              and b.status not in ('CANCELLED', 'REFUNDED', 'COMPLETED')
              and not exists (select 1 from assignments a where a.booking_id = b.id and a.status <> 'CANCELLED')
            order by b.travel_date, b.reference limit 200""",
            (rs, i) -> new UnassignedBooking(rs.getString(1), (UUID) rs.getObject(2), rs.getString(3), rs.getString(4), rs.getObject(5, LocalDate.class),
                rs.getInt(6), rs.getString(7)));
        var open = jdbc.query("""
            select d.id, d.number::text, d.customer_id, d.customer_name, d.due_date, d.currency, d.total_cents, d.paid_cents,
              coalesce((select sum(p.amount_cents) from billing_payments p where p.document_id = d.id and p.status = 'PENDING'), 0),
              d.due_date is not null and d.due_date < current_date
            from billing_documents d where d.kind = 'INVOICE' and d.status = 'ISSUED' and d.paid_cents < d.total_cents
            order by (d.due_date is not null and d.due_date < current_date) desc, d.due_date nulls last, d.number limit 200""",
            (rs, i) -> new OpenInvoice((UUID) rs.getObject(1), rs.getString(2), (UUID) rs.getObject(3), rs.getString(4), rs.getObject(5, LocalDate.class),
                rs.getString(6), rs.getLong(7), rs.getLong(8), rs.getLong(9), rs.getBoolean(10)));
        var toReview = jdbc.query("""
            select p.id, d.id, d.number::text, d.customer_name, p.amount_cents, p.paid_on, p.method, p.reference,
              (select count(*) from billing_attachments a where a.payment_id = p.id), p.recorded_at
            from billing_payments p join billing_documents d on d.id = p.document_id where p.status = 'PENDING' order by p.recorded_at limit 200""",
            (rs, i) -> new PaymentToReview((UUID) rs.getObject(1), (UUID) rs.getObject(2), rs.getString(3), rs.getString(4), rs.getLong(5),
                rs.getObject(6, LocalDate.class), rs.getString(7), rs.getString(8), rs.getInt(9), rs.getObject(10, OffsetDateTime.class)));
        // Customer uploads (no payment yet) on invoices that still have a balance and no payment recorded since the upload.
        var proofs = jdbc.query("""
            select a.id, d.id, d.number::text, d.customer_name, a.uploaded_by, a.uploaded_at
            from billing_attachments a join billing_documents d on d.id = a.document_id
            where a.payment_id is null and d.kind = 'INVOICE' and d.status = 'ISSUED' and d.paid_cents < d.total_cents
              and not exists (select 1 from billing_payments p where p.document_id = d.id and p.recorded_at > a.uploaded_at and p.status <> 'REJECTED')
            order by a.uploaded_at limit 200""",
            (rs, i) -> new CustomerProof((UUID) rs.getObject(1), (UUID) rs.getObject(2), rs.getString(3), rs.getString(4), rs.getString(5),
                rs.getObject(6, OffsetDateTime.class)));
        Integer duplicates = jdbc.queryForObject("""
            select (select count(*) from (select 1 from customers where merged_into_id is null and phone_digits is not null group by phone_digits having count(*) > 1) p)
                 + (select count(*) from (select 1 from customers where merged_into_id is null group by lower(btrim(name)) having count(*) > 1) n)""", Integer.class);
        return new WorkQueues(followUps, unassigned, open, toReview, proofs, duplicates == null ? 0 : duplicates);
    }
}
