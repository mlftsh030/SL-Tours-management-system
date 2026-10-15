package com.sltours.customers;

import com.sltours.billing.dto.BillingDtos.*;
import com.sltours.billing.model.BillingTypes.*;
import com.sltours.billing.service.*;
import com.sltours.bookings.dto.CreateBookingRequest;
import com.sltours.bookings.service.BookingService;
import com.sltours.customers.dto.CustomerDtos.*;
import com.sltours.customers.service.CustomerRecordsService;
import com.sltours.enquiries.dto.CreateEnquiryRequest;
import com.sltours.enquiries.service.EnquiryService;
import com.sltours.partners.dto.PartnerDtos;
import com.sltours.partners.model.PartnerEnums.PaymentKind;
import com.sltours.payments.service.BookingPaymentService;
import com.sltours.reports.service.WorkQueueService;
import com.sltours.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The operational data model on PostgreSQL with every migration: customer records, the enquiry -> quotation -> booking
 * -> invoice -> payment chain, payment allocation and reversal, merges, work queues and exports.
 */
@AutoConfigureMockMvc
@WithMockUser(username = "admin@test.local", roles = "ADMIN")
class DataModelIntegrationTest extends PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired BookingService bookings;
    @Autowired EnquiryService enquiries;
    @Autowired BillingDocumentService documents;
    @Autowired BillingPaymentService payments;
    @Autowired QuotationBookingService quotationBookings;
    @Autowired BookingPaymentService bookingLedger;
    @Autowired BillingSettingsService settings;
    @Autowired NumberAllocator numbers;
    @Autowired CustomerRecordsService customers;
    @Autowired WorkQueueService queues;

    @BeforeEach void confirmNumbering() {
        for (var series : NumberAllocator.Series.values())
            settings.updateSequence(series, new SequenceUpdate(numbers.highestUsed(series) + 1, true));
    }

    static String email(String who) { return who + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com"; }
    String book(String email, String name, String phone, int daysAhead) {
        return bookings.createBooking(new CreateBookingRequest("cape-peninsula-journey", LocalDate.now().plusDays(daysAhead), 6, name, email, phone,
            null, "Sea Point", null, null)).reference();
    }
    UUID customerOfBooking(String reference) { return jdbc.queryForObject("select customer_id from bookings where reference = ?", UUID.class, reference); }
    String enquiry(String email) {
        return enquiries.submit(new CreateEnquiryRequest(UUID.randomUUID(), "Thandi M", email, "+27 82 555 0101", "Cape Point for six, with a stop at Boulders",
            null, null, LocalDate.now().plusDays(30), "Sea Point", 6, null, null, null)).reference();
    }
    DocumentContent quoted(String email, Integer passengers, LocalDate start) {
        Line tour = new Line("Cape Peninsula & Cape Point", List.of(new Leg("Sea Point hotel", List.of("Boulders Beach", "Cape Point"))), null,
            new BigDecimal("3"), "person", 210_000L, LineKind.SERVICE, null, null, null);
        Line transfer = new Line("Airport transfer", List.of(new Leg("Cape Town International", List.of("Sea Point hotel"))), null,
            BigDecimal.ONE, null, 85_000L, LineKind.SERVICE, null, null, null);
        return new DocumentContent(new Customer("Thandi M", null, email, "082 555 0101"), null, null, start, null, null, null, passengers, null, null,
            List.of(tour, transfer), new Discount(DiscountType.PERCENT, 1000, "Group discount"), false, null, null, null);
    }
    DocumentDetail issuedQuotation(DocumentContent c, String enquiryReference) {
        DocumentDetail d = documents.create(new SaveDocument(Kind.QUOTATION, UUID.randomUUID(), null, null, c, null, enquiryReference));
        return documents.issue(d.summary().id(), d.version());
    }

    @Test void customersAreMatchedByEmailOnlyAndPossibleDuplicatesAreSuggestedNotMerged() {
        String thandi = email("thandi");
        String first = book(thandi, "Thandi M", "082 555 0101", 20);
        String second = book(thandi.toUpperCase(Locale.ROOT), "T. Mokoena", null, 25);
        String enq = enquiry(thandi);
        UUID customer = customerOfBooking(first);
        assertThat(customer).isNotNull().isEqualTo(customerOfBooking(second));
        assertThat(jdbc.queryForObject("select customer_id from enquiries where reference = ?", UUID.class, enq)).isEqualTo(customer);
        assertThat(jdbc.queryForObject("select phone_digits from customers where id = ?", String.class, customer)).isEqualTo("27825550101");

        // Same name and phone, different email: two records, offered to staff as a possible duplicate.
        String other = book(email("thandi-work"), "Thandi M", "+27 82 555 0101", 22);
        UUID otherCustomer = customerOfBooking(other);
        assertThat(otherCustomer).isNotEqualTo(customer);
        assertThat(customers.duplicates()).anySatisfy(g -> {
            assertThat(g.reason()).isEqualTo("SAME_PHONE");
            assertThat(g.customers()).extracting(CustomerSummary::id).contains(customer, otherCustomer);
        });
        CustomerDetail detail = customers.detail(customer);
        assertThat(detail.bookings()).extracting(BookingLine::reference).containsExactlyInAnyOrder(first, second);
        assertThat(detail.enquiries()).extracting(EnquiryLine::reference).containsExactly(enq);
        assertThat(customers.search("555 0101", 0, 50).content()).extracting(CustomerSummary::id).contains(customer, otherCustomer);
        assertThat(customers.search(first, 0, 50).content()).extracting(CustomerSummary::id).containsExactly(customer);
    }

    @Test void anAcceptedQuotationBecomesOneBookingThenOneInvoiceAndConfirmedPaymentsReachTheBooking() throws Exception {
        String address = email("journey");
        String enq = enquiry(address);
        DocumentDetail quote = issuedQuotation(quoted(address, 3, LocalDate.now().plusDays(40)), enq);
        long quotedTotal = quote.totals().total();
        assertThat(quotedTotal).isEqualTo((3 * 210_000L + 85_000L) * 9 / 10);    // 10% discount, exact cents
        assertThat(quote.enquiryReference()).isEqualTo(enq);

        // Two simultaneous clicks: one booking.
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<QuotationBookingService.Result> convert = () -> {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin@test.local", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            try { return quotationBookings.convert(quote.summary().id(), null); } finally { SecurityContextHolder.clearContext(); }
        };
        var a = pool.submit(convert); var b = pool.submit(convert);
        var ra = a.get(30, TimeUnit.SECONDS); var rb = b.get(30, TimeUnit.SECONDS);
        pool.shutdown();
        assertThat(ra.bookingId()).isEqualTo(rb.bookingId());
        assertThat(List.of(ra.created(), rb.created())).containsExactlyInAnyOrder(true, false);
        assertThat(jdbc.queryForObject("select count(*) from bookings where source_quotation_id = ?", Integer.class, quote.summary().id())).isEqualTo(1);

        UUID bookingId = ra.bookingId();
        Map<String, Object> booking = jdbc.queryForMap("select * from bookings where id = ?", bookingId);
        assertThat(booking).containsEntry("booking_type", "QUOTED").containsEntry("total_amount_cents", (int) quotedTotal).containsEntry("guests", 3)
            .containsEntry("status", "AWAITING_PAYMENT").containsEntry("customer_email", address).containsEntry("pickup_location", "Sea Point hotel");
        assertThat(booking.get("title")).isEqualTo("Cape Peninsula & Cape Point and 1 more");
        assertThat(jdbc.queryForList("select title, line_total_cents from booking_services where booking_id = ? order by line_order", bookingId))
            .extracting(r -> r.get("title") + "=" + r.get("line_total_cents")).containsExactly("Cape Peninsula & Cape Point=630000", "Airport transfer=85000");
        assertThat(jdbc.queryForList("select leg_order || ':' || stop_order || ':' || kind || ':' || location from booking_stops where booking_id = ? order by 1",
            String.class, bookingId)).containsExactly("1:1:PICKUP:Sea Point hotel", "1:2:DROP_OFF:Boulders Beach", "1:3:DROP_OFF:Cape Point",
                "2:1:PICKUP:Cape Town International", "2:2:DROP_OFF:Sea Point hotel");
        assertThat(jdbc.queryForObject("select status from enquiries where reference = ?", String.class, enq)).isEqualTo("CONVERTED");
        assertThat(jdbc.queryForObject("select converted_booking_id from enquiries where reference = ?", UUID.class, enq)).isEqualTo(bookingId);
        DocumentDetail accepted = documents.get(quote.summary().id());
        assertThat(accepted.summary().status()).isEqualTo(Status.ACCEPTED);
        assertThat(accepted.linkedBookingReference()).isEqualTo(ra.bookingReference());

        // Later edits to the catalogue and customer record do not touch the agreed booking or the issued quotation.
        jdbc.update("update tours set base_price_cents = base_price_cents + 50000 where slug = 'cape-peninsula-journey'");
        jdbc.update("update customers set name = 'Thandi Mokoena-Smith' where email = ?", address);
        assertThat(jdbc.queryForObject("select total_amount_cents from bookings where id = ?", Integer.class, bookingId)).isEqualTo((int) quotedTotal);
        assertThat(documents.get(quote.summary().id()).view().info()).anySatisfy(r -> assertThat(r.value()).isEqualTo("Thandi M"));
        jdbc.update("update tours set base_price_cents = base_price_cents - 50000 where slug = 'cape-peninsula-journey'");

        // The invoice for the quotation belongs to the booking.
        ConversionResult inv = documents.convert(quote.summary().id());
        DocumentDetail draft = documents.get(inv.invoiceId());
        assertThat(draft.linkedBookingReference()).isEqualTo(ra.bookingReference());
        DocumentDetail invoice = documents.issue(inv.invoiceId(), draft.version());
        assertThat(invoice.totals().total()).isEqualTo(quotedTotal);

        // A payment is recorded on the invoice; the booking changes only when it is confirmed, exactly once.
        String ref = ra.bookingReference();
        assertThatThrownBy(() -> bookingLedger.record(ref, new PartnerDtos.NewPayment(UUID.randomUUID(), PaymentKind.PAYMENT, 100_000, "EFT", "direct",
            LocalDate.now(), null))).hasMessageContaining("Record the payment on invoice " + invoice.summary().number());
        DocumentDetail pending = payments.record(invoice.summary().id(), new NewPayment(UUID.randomUUID(), 200_000L, LocalDate.now(), PaymentMethod.EFT, "dep", null));
        assertThat(paid(bookingId)).isZero();
        UUID paymentId = pending.payments().get(0).id();
        DocumentDetail confirmed = payments.confirm(paymentId, new PaymentDecision(pending.payments().get(0).version(), null));
        payments.confirm(paymentId, new PaymentDecision(confirmed.payments().get(0).version(), null));   // retry
        assertThat(paid(bookingId)).isEqualTo(200_000);
        assertThat(jdbc.queryForObject("select count(*) from payments where billing_payment_id = ?", Integer.class, paymentId)).isEqualTo(1);
        assertThat(confirmed.summary().balanceCents()).isEqualTo(quotedTotal - 200_000);

        // Reversal: invoice and booking go back, receipt stays on file, history kept.
        assertThatThrownBy(() -> payments.reverse(paymentId, new PaymentDecision(confirmed.payments().get(0).version(), " "))).hasMessageContaining("Say why");
        DocumentDetail reversed = payments.reverse(paymentId, new PaymentDecision(confirmed.payments().get(0).version(), "Bounced: insufficient funds"));
        payments.reverse(paymentId, new PaymentDecision(reversed.payments().get(0).version(), "retry"));
        assertThat(reversed.summary().paymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(reversed.payments().get(0).status()).isEqualTo(PaymentState.REVERSED);
        assertThat(reversed.payments().get(0).reverseReason()).isEqualTo("Bounced: insufficient funds");
        assertThat(payments.receipt(paymentId, false).bytes()).isNotEmpty();
        assertThat(paid(bookingId)).isZero();
        assertThat(jdbc.queryForList("select allocation_of from payments where billing_payment_id = ? order by recorded_at", String.class, paymentId))
            .containsExactly("PAYMENT", "REVERSAL");
        assertThat(reversed.events()).extracting(EventView::event).contains("PAYMENT_CONFIRMED", "PAYMENT_REVERSED");
        assertThatThrownBy(() -> bookingLedger.record(ref, new PartnerDtos.NewPayment(UUID.randomUUID(), PaymentKind.REFUND, 1, "EFT", "refund",
            LocalDate.now(), null))).hasMessageContaining("Reverse the invoice payment");

        // An invoice paid above the booking total only allocates up to the booking total.
        DocumentDetail rest = payments.record(invoice.summary().id(), new NewPayment(UUID.randomUUID(), quotedTotal, LocalDate.now(), PaymentMethod.EFT, "full", null));
        UUID full = rest.payments().get(1).id();
        jdbc.update("update bookings set total_amount_cents = total_amount_cents - 1000 where id = ?", bookingId);
        DocumentDetail fullyPaid = payments.confirm(full, new PaymentDecision(rest.payments().get(1).version(), null));
        assertThat(fullyPaid.summary().paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(paid(bookingId)).isEqualTo((int) quotedTotal - 1000);
        assertThat(fullyPaid.events()).extracting(EventView::event).contains("BOOKING_PARTLY_UPDATED");
    }

    int paid(UUID bookingId) { return jdbc.queryForObject("select amount_paid_cents from bookings where id = ?", Integer.class, bookingId); }

    @Test void quotationsWithoutDateOrPassengersAskForThemAndDraftsCannotBecomeBookings() {
        String address = email("missing");
        DocumentDetail quote = issuedQuotation(quoted(address, null, null), null);
        assertThatThrownBy(() -> quotationBookings.convert(quote.summary().id(), null)).hasMessageContaining("travel date");
        assertThatThrownBy(() -> quotationBookings.convert(quote.summary().id(), new QuotationBookingService.Request(LocalDate.now().plusDays(9), null)))
            .hasMessageContaining("passengers");
        var r = quotationBookings.convert(quote.summary().id(), new QuotationBookingService.Request(LocalDate.now().plusDays(9), 4));
        assertThat(jdbc.queryForObject("select guests from bookings where id = ?", Integer.class, r.bookingId())).isEqualTo(4);
        DocumentDetail draft = documents.create(new SaveDocument(Kind.QUOTATION, UUID.randomUUID(), null, null, quoted(address, 2, LocalDate.now().plusDays(5)), null));
        assertThatThrownBy(() -> quotationBookings.convert(draft.summary().id(), null)).hasMessageContaining("Issue the quotation");
    }

    @Test void mergingMovesHistoryKeepsOwnershipAndIsRecorded() {
        String a = email("merge-a"), b = email("merge-b");
        String bookingA = book(a, "Lerato K", "083 111 2222", 30);
        String bookingB = book(b, "Lerato K", "083 111 2222", 31);
        UUID survivor = customerOfBooking(bookingA), merged = customerOfBooking(bookingB);
        UUID account = UUID.randomUUID();
        jdbc.update("insert into user_accounts (id, email, name, password_hash, role, enabled, created_at, email_verified_at) values (?, ?, 'Lerato', 'x', 'CUSTOMER', true, now(), now())", account, b);
        jdbc.update("update customers set account_id = ? where id = ?", account, merged);
        jdbc.update("update bookings set account_id = ? where reference = ?", account, bookingB);

        CustomerDetail s = customers.detail(survivor), m = customers.detail(merged);
        CustomerDetail result = customers.merge(new MergeRequest(survivor, merged, s.version(), m.version(), "Same person: confirmed by phone"));
        assertThat(result.bookings()).extracting(BookingLine::reference).containsExactlyInAnyOrder(bookingA, bookingB);
        assertThat(result.summary().hasAccount()).isTrue();
        assertThat(jdbc.queryForObject("select account_id from bookings where reference = ?", UUID.class, bookingB)).isEqualTo(account);   // ownership unchanged
        assertThat(jdbc.queryForObject("select account_id from bookings where reference = ?", UUID.class, bookingA)).isNull();
        assertThat(jdbc.queryForObject("select merged_into_id from customers where id = ?", UUID.class, merged)).isEqualTo(survivor);
        assertThat(jdbc.queryForObject("select moved::text from customer_merges where merged_id = ?", String.class, merged)).contains(
            jdbc.queryForObject("select id::text from bookings where reference = ?", String.class, bookingB));
        assertThat(result.merges()).singleElement().satisfies(x -> assertThat(x.reason()).isEqualTo("Same person: confirmed by phone"));
        // New records with the merged address now resolve to the survivor.
        assertThat(customerOfBooking(book(b, "Lerato", null, 33))).isEqualTo(survivor);
        assertThat(customers.search(null, 0, 100).content()).extracting(CustomerSummary::id).doesNotContain(merged);

        // Two customers with their own logins are never merged.
        String c = email("merge-c");
        UUID third = customerOfBooking(book(c, "Lerato K", null, 34));
        UUID other = UUID.randomUUID();
        jdbc.update("insert into user_accounts (id, email, name, password_hash, role, enabled, created_at, email_verified_at) values (?, ?, 'L', 'x', 'CUSTOMER', true, now(), now())", other, c);
        jdbc.update("update customers set account_id = ? where id = ?", other, third);
        CustomerDetail s2 = customers.detail(survivor), t = customers.detail(third);
        assertThatThrownBy(() -> customers.merge(new MergeRequest(survivor, third, s2.version(), t.version(), "x"))).hasMessageContaining("own login account");
    }

    @Test void workQueuesShowWhatNeedsActionAndExportsAreAdminOnlyAndSafe() throws Exception {
        String address = email("queue");
        String booking = book(address, "=HYPERLINK(\"http://evil\")", null, 3);
        var q = queues.queues();
        assertThat(q.unassignedBookings()).extracting(x -> x.reference()).contains(booking);

        mvc.perform(get("/api/v1/admin/exports/bookings").param("from", LocalDate.now().toString()).param("to", LocalDate.now().plusDays(10).toString())
            .with(user("staff@test.local").roles("STAFF"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/exports/customers").param("from", "2020-01-01").param("to", LocalDate.now().toString())
            .with(user("customer@test.local").roles("CUSTOMER"))).andExpect(status().isForbidden());
        String csv = mvc.perform(get("/api/v1/admin/exports/bookings").param("from", LocalDate.now().toString()).param("to", LocalDate.now().plusDays(10).toString())
                .with(user("admin@test.local").roles("ADMIN")))
            .andExpect(status().isOk()).andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("sl-tours-bookings-")))
            .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).contains(booking).contains("\"'=HYPERLINK(\"\"http://evil\"\")\"").doesNotContain(",\"=HYPERLINK");
        assertThat(jdbc.queryForObject("select count(*) from admin_audit_events where action = 'DATA_EXPORTED' and entity_id = 'BOOKINGS' and actor = 'admin@test.local'",
            Integer.class)).isPositive();
        mvc.perform(get("/api/v1/admin/work-queues").with(user("staff@test.local").roles("STAFF"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/customers/duplicates").with(user("staff@test.local").roles("STAFF"))).andExpect(status().isOk());
    }

    @Test void testDatabaseIsTheDisposableContainerNeverTheDevelopmentDatabase() {
        String url = jdbc.queryForObject("select current_database()", String.class);
        assertThat(url).isEqualTo("test");
        assertThat(jdbc.queryForObject("select count(*) from customers where email like 'e2e-%'", Integer.class)).isZero();
    }
}
