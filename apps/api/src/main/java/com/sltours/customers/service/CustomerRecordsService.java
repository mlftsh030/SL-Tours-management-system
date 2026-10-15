package com.sltours.customers.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sltours.audit.AuditService;
import com.sltours.customers.dto.CustomerDtos.*;
import com.sltours.shared.dto.PageResponse;
import com.sltours.shared.error.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Staff tools for customer records: search, full history, corrections, possible duplicates and audited merges.
 *
 * <p>A customer record is operational (name, contact details, history). Logging in is a separate {@code user_accounts}
 * row, attached to the customer only after the account verified the email address. Merging moves operational history
 * to the surviving record; it never changes which login account owns a booking, enquiry or invoice.
 */
@Service
public class CustomerRecordsService {
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final SessionRegistry sessions;
    private final ObjectMapper json;
    public CustomerRecordsService(JdbcTemplate jdbc, AuditService audit, SessionRegistry sessions, ObjectMapper json) {
        this.jdbc = jdbc; this.audit = audit; this.sessions = sessions; this.json = json;
    }

    /** One row per live customer with history counts. Outstanding = issued invoices' unpaid balance (rand). */
    private static final String SUMMARY = """
        select c.id, c.name, c.email, c.phone, c.account_id is not null as has_account, a.last_login_at, a.email as account_email, c.demo_data,
          (select count(*) from enquiries e where e.customer_id = c.id) as enquiries,
          (select count(*) from enquiries e where e.customer_id = c.id and e.status in ('NEW', 'IN_PROGRESS', 'AWAITING_CUSTOMER')) as open_enquiries,
          (select count(*) from bookings b where b.customer_id = c.id) as bookings,
          (select count(*) from bookings b where b.customer_id = c.id and b.travel_date >= current_date and b.status not in ('CANCELLED', 'REFUNDED', 'COMPLETED')) as upcoming,
          (select count(*) from billing_documents d where d.customer_id = c.id and d.kind = 'INVOICE' and d.status <> 'DRAFT') as invoices,
          (select coalesce(sum(d.total_cents - d.paid_cents), 0) from billing_documents d where d.customer_id = c.id and d.kind = 'INVOICE' and d.status = 'ISSUED') as outstanding,
          greatest(c.updated_at,
            (select max(e.updated_at) from enquiries e where e.customer_id = c.id),
            (select max(b.updated_at) from bookings b where b.customer_id = c.id),
            (select max(d.updated_at) from billing_documents d where d.customer_id = c.id)) as last_activity
        from customers c left join user_accounts a on a.id = c.account_id
        """;

    @Transactional(readOnly = true)
    public PageResponse<CustomerSummary> search(String q, int page, int size) {
        Map<String, Integer> online = onlineSessions();
        String where = " where c.merged_into_id is null";
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(q)) {
            String term = q.trim().toLowerCase(Locale.ROOT);
            String like = "%" + term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            String digits = term.replaceAll("\\D", "");
            where += " and (lower(c.name) like ? or c.email like ? or (? <> '' and c.phone_digits like ?)"
                + " or exists (select 1 from bookings b where b.customer_id = c.id and lower(b.reference) = ?)"
                + " or exists (select 1 from enquiries e where e.customer_id = c.id and lower(e.reference) = ?)"
                + " or exists (select 1 from billing_documents d where d.customer_id = c.id and d.number::text = ?))";
            args.addAll(List.of(like, like, digits, "%" + digits + "%", term, term, term));
        }
        long total = jdbc.queryForObject("select count(*) from customers c" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args); pageArgs.add(size); pageArgs.add((long) page * size);
        List<CustomerSummary> rows = jdbc.query("select * from (" + SUMMARY + where + ") s order by s.last_activity desc nulls last, s.name limit ? offset ?",
            (rs, i) -> summary(rs, online), pageArgs.toArray());
        return new PageResponse<>(rows, page, (int) Math.ceil(total / (double) size), total);
    }

    @Transactional(readOnly = true)
    public CustomerDetail detail(UUID id) {
        Map<String, Integer> online = onlineSessions();
        CustomerSummary s = jdbc.query(SUMMARY + " where c.id = ?", (rs, i) -> summary(rs, online), id).stream().findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        Map<String, Object> extra = jdbc.queryForMap("""
            select c.version, c.country, c.notes, c.merged_into_id, a.email as account_email, a.email_verified_at is not null as verified
            from customers c left join user_accounts a on a.id = c.account_id where c.id = ?""", id);
        var enquiries = jdbc.query("""
            select e.reference, e.status, e.enquiry_type, e.source, t.title, e.requested_date, e.created_at, e.updated_at
            from enquiries e left join tours t on t.id = e.tour_id where e.customer_id = ? order by e.created_at desc""",
            (rs, i) -> new EnquiryLine(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                rs.getObject(6, LocalDate.class), rs.getObject(7, OffsetDateTime.class), rs.getObject(8, OffsetDateTime.class)), id);
        var bookings = jdbc.query("""
            select b.reference, coalesce(b.title, t.title, 'Private trip'), b.status, b.travel_date, b.guests, b.total_amount_cents, b.amount_paid_cents,
              exists (select 1 from assignments a where a.booking_id = b.id and a.status <> 'CANCELLED'), q.number
            from bookings b left join tours t on t.id = b.tour_id left join billing_documents q on q.id = b.source_quotation_id
            where b.customer_id = ? order by b.travel_date desc""",
            (rs, i) -> new BookingLine(rs.getString(1), rs.getString(2), rs.getString(3), rs.getObject(4, LocalDate.class), rs.getInt(5), rs.getLong(6),
                rs.getLong(7), rs.getBoolean(8), rs.getString(9)), id);
        var documents = jdbc.query("""
            select d.id, d.kind, d.status, coalesce(d.number::text, 'draft'), d.issue_date, d.due_date, d.currency, d.total_cents, d.paid_cents,
              d.payment_status, d.booking_reference
            from billing_documents d where d.customer_id = ? order by d.created_at desc""",
            (rs, i) -> new DocumentLine((UUID) rs.getObject(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getObject(5, LocalDate.class),
                rs.getObject(6, LocalDate.class), rs.getString(7), rs.getLong(8), rs.getLong(9), rs.getString(10), rs.getString(11)), id);
        var payments = jdbc.query("""
            select p.id, d.id, d.number::text, p.amount_cents, p.paid_on, p.method, p.status, p.receipt_number, p.recorded_at
            from billing_payments p join billing_documents d on d.id = p.document_id where d.customer_id = ? order by p.recorded_at desc""",
            (rs, i) -> new PaymentLine((UUID) rs.getObject(1), (UUID) rs.getObject(2), rs.getString(3), rs.getLong(4), rs.getObject(5, LocalDate.class),
                rs.getString(6), rs.getString(7), (Long) rs.getObject(8), rs.getObject(9, OffsetDateTime.class)), id);
        var merges = jdbc.query("""
            select o.id, o.name, o.email, case when m.survivor_id = ? then 'MERGED_IN' else 'MERGED_INTO' end, m.reason, m.merged_by, m.merged_at
            from customer_merges m join customers o on o.id = case when m.survivor_id = ? then m.merged_id else m.survivor_id end
            where m.survivor_id = ? or m.merged_id = ? order by m.merged_at desc""",
            (rs, i) -> new MergeLine((UUID) rs.getObject(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                rs.getObject(7, OffsetDateTime.class)), id, id, id, id);
        return new CustomerDetail(s, ((Number) extra.get("version")).longValue(), (String) extra.get("country"), (String) extra.get("notes"),
            (String) extra.get("account_email"), Boolean.TRUE.equals(extra.get("verified")), (UUID) extra.get("merged_into_id"),
            enquiries, bookings, documents, payments, merges);
    }

    /** Corrects the operational record. Issued documents keep the details they were issued with. */
    @Transactional
    public CustomerDetail update(UUID id, UpdateCustomer r) {
        int n = jdbc.update("""
            update customers set name = ?, phone = ?, phone_digits = sl_phone_digits(?), country = ?, notes = ?, version = version + 1,
              updated_at = now(), updated_by = ? where id = ? and version = ? and merged_into_id is null""",
            r.name().trim(), blank(r.phone()), blank(r.phone()), blank(r.country()), blank(r.notes()), actor(), id, r.version());
        if (n == 0) {
            if (jdbc.queryForObject("select count(*) from customers where id = ?", Integer.class, id) == 0) throw new ResourceNotFoundException("Customer not found");
            throw new ConflictException("This customer changed or was merged. Reload and try again.");
        }
        audit.record("CUSTOMER_UPDATED", id.toString(), "name/phone/country/notes updated");
        return detail(id);
    }

    /**
     * Possible duplicates among live customers: the same phone number, or the same name with different email addresses.
     * These are suggestions for staff to review, never merged automatically.
     */
    @Transactional(readOnly = true)
    public List<DuplicateGroup> duplicates() {
        Map<String, Integer> online = onlineSessions();
        List<DuplicateGroup> groups = new ArrayList<>();
        for (String phone : jdbc.queryForList("""
                select phone_digits from customers where merged_into_id is null and phone_digits is not null
                group by phone_digits having count(*) > 1 order by phone_digits limit 100""", String.class))
            groups.add(new DuplicateGroup("SAME_PHONE", phone, jdbc.query(SUMMARY + " where c.merged_into_id is null and c.phone_digits = ? order by c.created_at",
                (rs, i) -> summary(rs, online), phone)));
        for (String name : jdbc.queryForList("""
                select lower(btrim(name)) from customers where merged_into_id is null group by lower(btrim(name)) having count(*) > 1
                order by 1 limit 100""", String.class))
            groups.add(new DuplicateGroup("SAME_NAME", name, jdbc.query(SUMMARY + " where c.merged_into_id is null and lower(btrim(c.name)) = ? order by c.created_at",
                (rs, i) -> summary(rs, online), name)));
        return groups;
    }

    /**
     * Moves the history of one customer record into another, keeping both rows and a record of every moved id.
     * Refused when both records have login accounts (two people may share a name or phone; two logins are two people
     * until they say otherwise). A login on the merged record moves to the survivor. Record ownership (account_id on
     * bookings, enquiries and documents) never changes.
     */
    @Transactional
    public CustomerDetail merge(MergeRequest r) {
        if (r.survivorId().equals(r.mergedId())) throw new IllegalArgumentException("Choose two different customers");
        List<Map<String, Object>> rows = jdbc.queryForList("select id, version, account_id, merged_into_id, phone from customers where id in (?, ?) order by id for update",
            r.survivorId(), r.mergedId());
        if (rows.size() != 2) throw new ResourceNotFoundException("Customer not found");
        Map<String, Object> survivor = rows.stream().filter(x -> x.get("id").equals(r.survivorId())).findFirst().orElseThrow();
        Map<String, Object> merged = rows.stream().filter(x -> x.get("id").equals(r.mergedId())).findFirst().orElseThrow();
        if (survivor.get("merged_into_id") != null || merged.get("merged_into_id") != null) throw new IllegalArgumentException("One of these customers was already merged");
        if (((Number) survivor.get("version")).longValue() != r.survivorVersion() || ((Number) merged.get("version")).longValue() != r.mergedVersion())
            throw new ConflictException("One of these customers changed. Reload and try again.");
        if (survivor.get("account_id") != null && merged.get("account_id") != null)
            throw new IllegalArgumentException("Both customers have their own login account, so they are treated as different people. Ask the customer which account to keep.");

        Map<String, List<String>> moved = new LinkedHashMap<>();
        for (String table : List.of("bookings", "enquiries", "billing_documents", "trip_planning_requests")) {
            List<String> ids = jdbc.queryForList("update " + table + " set customer_id = ? where customer_id = ? returning id::text", String.class, r.survivorId(), r.mergedId());
            moved.put(table, ids);
        }
        List<String> earlier = jdbc.queryForList("update customers set merged_into_id = ? where merged_into_id = ? returning id::text", String.class, r.survivorId(), r.mergedId());
        moved.put("customers_previously_merged", earlier);
        UUID account = (UUID) merged.get("account_id");
        jdbc.update("""
            update customers set merged_into_id = ?, merged_at = now(), merged_by = ?, account_id = null, version = version + 1, updated_at = now(), updated_by = ?
            where id = ?""", r.survivorId(), actor(), actor(), r.mergedId());
        jdbc.update("""
            update customers s set account_id = coalesce(s.account_id, ?), phone = coalesce(s.phone, ?), phone_digits = coalesce(s.phone_digits, sl_phone_digits(?)),
              version = version + 1, updated_at = now(), updated_by = ? where s.id = ?""",
            account, merged.get("phone"), merged.get("phone"), actor(), r.survivorId());
        if (account != null) moved.put("login_account", List.of(account.toString()));
        try {
            jdbc.update("insert into customer_merges (id, survivor_id, merged_id, reason, moved, merged_by, merged_at) values (?, ?, ?, ?, ?::jsonb, ?, now())",
                UUID.randomUUID(), r.survivorId(), r.mergedId(), r.reason().trim(), json.writeValueAsString(moved), actor());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException(e); }
        audit.record("CUSTOMER_MERGED", r.survivorId().toString(), "merged " + r.mergedId() + ": " + r.reason().trim());
        return detail(r.survivorId());
    }

    private CustomerSummary summary(ResultSet rs, Map<String, Integer> online) throws SQLException {
        String accountEmail = rs.getString("account_email");
        return new CustomerSummary((UUID) rs.getObject("id"), rs.getString("name"), rs.getString("email"), rs.getString("phone"), rs.getBoolean("has_account"),
            rs.getObject("last_login_at", OffsetDateTime.class), accountEmail == null ? 0 : online.getOrDefault(accountEmail, 0), rs.getInt("enquiries"),
            rs.getInt("open_enquiries"), rs.getInt("bookings"), rs.getInt("upcoming"), rs.getInt("invoices"), rs.getLong("outstanding"),
            rs.getObject("last_activity", OffsetDateTime.class), rs.getBoolean("demo_data"));
    }

    private Map<String, Integer> onlineSessions() {
        Map<String, Integer> online = new HashMap<>();
        for (Object principal : sessions.getAllPrincipals())
            if (principal instanceof UserDetails user) online.put(user.getUsername(), sessions.getAllSessions(principal, false).size());
        return online;
    }
    private static String blank(String v) { return StringUtils.hasText(v) ? v.trim() : null; }
    private static String actor() {
        var a = SecurityContextHolder.getContext().getAuthentication();
        return a == null ? "system" : a.getName();
    }
}
