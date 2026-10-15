package com.sltours.billing;
import com.sltours.billing.dto.BillingDtos.*;
import com.sltours.billing.model.BillingTypes.*;
import com.sltours.billing.service.*;
import com.sltours.bookings.model.Booking;
import com.sltours.shared.error.ConflictException;
import com.sltours.support.*;
import com.sltours.tours.repository.TourRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import static org.assertj.core.api.Assertions.*;

@WithMockUser(username = "admin@test.local", roles = "ADMIN")
class BillingWorkflowIntegrationTest extends PostgresIntegrationTest {
    @Autowired BillingDocumentService documents;
    @Autowired BillingPaymentService payments;
    @Autowired BillingSettingsService settings;
    @Autowired NumberAllocator numbers;
    @Autowired Fixtures fixtures;
    @Autowired TourRepository tours;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void confirmNumbering() {
        for (var series : NumberAllocator.Series.values())
            settings.updateSequence(series, new SequenceUpdate(numbers.highestUsed(series) + 1, true));
    }

    static DocumentContent content(String customer, long cents) {
        return new DocumentContent(new Customer(customer, null, "guest@example.com", "082 000 0000"), null, null, null, null, null, null, null, null, null,
            List.of(BillingFixtures.line("Transfer", List.of(new Leg("Airport", List.of("Sea Point"))), null, cents)), null, false, null, null, null);
    }
    DocumentDetail draft(Kind kind, DocumentContent c) {
        return documents.create(new SaveDocument(kind, UUID.randomUUID(), null, null, c, "Internal: customer pays late"));
    }
    DocumentDetail issued(Kind kind, DocumentContent c) {
        DocumentDetail d = draft(kind, c);
        return documents.issue(d.summary().id(), d.version());
    }
    static String text(byte[] pdf) throws Exception {
        try (var doc = Loader.loadPDF(pdf)) { return new PDFTextStripper().getText(doc); }
    }

    @Test void invoicesCannotBeIssuedUntilTheNextNumberIsConfirmed() {
        settings.updateSequence(NumberAllocator.Series.INVOICE, new SequenceUpdate(numbers.highestUsed(NumberAllocator.Series.INVOICE) + 1, false));
        DocumentDetail d = draft(Kind.INVOICE, content("Unconfirmed Ltd", 10_000));
        assertThat(d.numberingConfirmed()).isFalse();
        assertThatThrownBy(() -> documents.issue(d.summary().id(), d.version())).hasMessageContaining("Confirm the next invoice number");
        assertThat(documents.get(d.summary().id()).summary().status()).isEqualTo(Status.DRAFT);
    }

    @Test void numbersAreUniqueUnderConcurrencyRetriesAreIdempotentAndVoidedNumbersAreNeverReused() throws Exception {
        long start = numbers.highestUsed(NumberAllocator.Series.INVOICE) + 1;
        List<DocumentDetail> drafts = new ArrayList<>();
        for (int i = 0; i < 8; i++) drafts.add(draft(Kind.INVOICE, content("Concurrent " + i, 10_000 + i)));
        var auth = SecurityContextHolder.getContext().getAuthentication();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Long>> results = new ArrayList<>();
        for (DocumentDetail d : drafts) for (int attempt = 0; attempt < 2; attempt++) results.add(pool.submit(() -> {
            SecurityContextHolder.getContext().setAuthentication(auth);
            go.await();
            return documents.issue(d.summary().id(), d.version()).summary().number();
        }));
        go.countDown();
        List<Long> issued = new ArrayList<>();
        for (Future<Long> f : results) issued.add(f.get(60, TimeUnit.SECONDS));
        pool.shutdown();
        Set<Long> distinct = new TreeSet<>(issued);
        assertThat(distinct).hasSize(8);                       // two attempts per draft, one number each
        assertThat(distinct).containsExactlyElementsOf(java.util.stream.LongStream.range(start, start + 8).boxed().toList());

        DocumentDetail first = documents.get(drafts.get(0).summary().id());
        documents.voidDocument(first.summary().id(), new VoidRequest(first.version(), "Customer cancelled"));
        DocumentDetail next = issued(Kind.INVOICE, content("After void", 5_000));
        assertThat(next.summary().number()).isEqualTo(start + 8);
        assertThatThrownBy(() -> settings.updateSequence(NumberAllocator.Series.INVOICE, new SequenceUpdate(start + 3, true)))
            .hasMessageContaining("next number must be at least " + (start + 9));
    }

    @Test void bookingInvoicesKeepTheAgreedPriceAndIssuedDocumentsIgnoreLaterTourAndSettingsChanges() throws Exception {
        Booking booking = fixtures.tourBooking(2, LocalDate.now().plusDays(20));
        BookingPrefill prefill = documents.prefill(booking.getReference());
        assertThat(prefill.content().lines().get(0).unitPriceCents()).isEqualTo(100_000L);
        DocumentDetail d = documents.create(new SaveDocument(Kind.INVOICE, UUID.randomUUID(), null, booking.getReference(), prefill.content(), null));
        assertThat(d.summary().bookingReference()).isEqualTo(booking.getReference());
        DocumentDetail inv = documents.issue(d.summary().id(), d.version());
        byte[] before = documents.pdf(inv.summary().id()).bytes();

        var tour = booking.getTour();
        jdbc.update("update tours set base_price_cents = base_price_cents + 77700 where id = ?", tour.getId());
        var s = settings.view();
        settings.update(new SettingsUpdate(s.version(), s.companyName(), s.tagline(), s.phone(), s.whatsapp(), s.email(), s.website(), s.address(),
            s.addressNeedsReview(), s.bankAccountName(), "Changed Bank Ltd", s.bankAccountNumber(), s.bankAccountType(), s.bankBranchCode(),
            s.bankSwiftCode(), s.paymentInstructions(), s.invoiceTerms(), s.quotationTerms(), s.currency(), false, null, null, null, null, null));
        try {
            DocumentDetail after = documents.get(inv.summary().id());
            assertThat(after.totals().total()).isEqualTo(100_000L);
            assertThat(after.view().banking()).extracting(r -> r.value()).contains("FNB SA").doesNotContain("Changed Bank Ltd");
            assertThat(documents.pdf(inv.summary().id()).bytes()).isEqualTo(before);
            PreviewResult fresh = documents.preview(new Preview(Kind.INVOICE, content("New", 1_000), null));
            assertThat(fresh.view().banking()).extracting(r -> r.value()).contains("Changed Bank Ltd");
        } finally {
            var now = settings.view();
            settings.update(new SettingsUpdate(now.version(), now.companyName(), now.tagline(), now.phone(), now.whatsapp(), now.email(), now.website(),
                now.address(), now.addressNeedsReview(), now.bankAccountName(), "FNB SA", now.bankAccountNumber(), now.bankAccountType(),
                now.bankBranchCode(), now.bankSwiftCode(), now.paymentInstructions(), now.invoiceTerms(), now.quotationTerms(), now.currency(), false,
                null, null, null, null, null));
            jdbc.update("update tours set base_price_cents = base_price_cents - 77700 where id = ?", tour.getId());
        }
    }

    @Test void catalogueOverridesNeedAReasonAndTaxNeedsConfiguration() {
        var tour = tours.findAll().get(0);
        Line line = new Line(tour.getTitle(), List.of(), null, BigDecimal.valueOf(2), "person", tour.getBasePriceCents() + 10_000L, LineKind.SERVICE,
            tour.getSlug(), null, null);
        DocumentContent c = new DocumentContent(new Customer("Override", null, null, null), null, null, null, null, null, null, null, null, null,
            List.of(line), null, false, null, null, null);
        assertThatThrownBy(() -> draft(Kind.QUOTATION, c)).hasMessageContaining("Give a reason for the agreed price");
        Line reasoned = new Line(line.title(), List.of(), null, line.quantity(), line.unit(), line.unitPriceCents(), LineKind.SERVICE, tour.getSlug(),
            999L, "Repeat customer rate");
        DocumentDetail d = draft(Kind.QUOTATION, new DocumentContent(c.customer(), null, null, null, null, null, null, null, null, null, List.of(reasoned),
            null, false, null, null, null));
        assertThat(d.content().lines().get(0).cataloguePriceCents()).isEqualTo((long) tour.getBasePriceCents()); // the server's figure, not the browser's
        assertThat(d.totals().total()).isEqualTo(2 * (tour.getBasePriceCents() + 10_000L));
        DocumentContent taxed = new DocumentContent(c.customer(), null, null, null, null, null, null, null, null, null, List.of(reasoned), null, true,
            null, null, null);
        assertThatThrownBy(() -> draft(Kind.QUOTATION, taxed)).hasMessageContaining("Tax is not configured");
    }

    @Test void aQuotationConvertsToOneInvoiceWithoutRetyping() {
        DocumentDetail quote = issued(Kind.QUOTATION, BillingFixtures.youngLife());
        ConversionResult first = documents.convert(quote.summary().id());
        ConversionResult again = documents.convert(quote.summary().id());
        assertThat(first.created()).isTrue();
        assertThat(again.created()).isFalse();
        assertThat(again.invoiceId()).isEqualTo(first.invoiceId());
        DocumentDetail invoice = documents.get(first.invoiceId());
        assertThat(invoice.summary().kind()).isEqualTo(Kind.INVOICE);
        assertThat(invoice.content().lines()).isEqualTo(BillingFixtures.youngLife().lines());
        assertThat(invoice.totals().total()).isEqualTo(285_000L);
        assertThat(invoice.sourceQuotation().id()).isEqualTo(quote.summary().id());
        DocumentDetail accepted = documents.get(quote.summary().id());
        assertThat(accepted.summary().status()).isEqualTo(Status.ACCEPTED);
        assertThat(accepted.convertedInvoice().id()).isEqualTo(first.invoiceId());
        assertThatThrownBy(() -> documents.voidDocument(quote.summary().id(), new VoidRequest(accepted.version(), "x")))
            .hasMessageContaining("Void the invoice first");
    }

    @Test void partialAndFullPaymentsCountOnlyOnceConfirmedAndProduceReceipts() throws Exception {
        DocumentDetail inv = issued(Kind.INVOICE, BillingFixtures.youngLife());
        UUID id = inv.summary().id();
        UUID key = UUID.randomUUID();
        DocumentDetail pending = payments.record(id, new NewPayment(key, 100_000L, LocalDate.now(), PaymentMethod.EFT, "YL deposit", null));
        payments.record(id, new NewPayment(key, 100_000L, LocalDate.now(), PaymentMethod.EFT, "YL deposit", null)); // retry
        assertThat(pending.payments()).hasSize(1);
        assertThat(documents.get(id).payments()).hasSize(1);
        assertThat(pending.summary().paymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(pending.summary().balanceCents()).isEqualTo(285_000L);
        assertThat(pending.pendingCents()).isEqualTo(100_000L);
        assertThatThrownBy(() -> payments.record(id, new NewPayment(UUID.randomUUID(), 200_000L, LocalDate.now(), PaymentMethod.EFT, null, null)))
            .hasMessageContaining("R1 850 still open");

        UUID paymentId = pending.payments().get(0).id();
        payments.attachProof(paymentId, new MockMultipartFile("file", "pop.pdf", "application/pdf", "%PDF-1.7 proof".getBytes()), "Bank POP");
        DocumentDetail withProof = documents.get(id);
        assertThat(withProof.summary().paymentStatus()).isEqualTo(PaymentStatus.UNPAID);   // proof is evidence only
        assertThat(withProof.payments().get(0).attachments()).hasSize(1);

        DocumentDetail partly = payments.confirm(paymentId, new PaymentDecision(withProof.payments().get(0).version(), null));
        assertThat(partly.summary().paymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_PAID);
        assertThat(partly.summary().balanceCents()).isEqualTo(185_000L);
        Long receipt = partly.payments().get(0).receiptNumber();
        assertThat(receipt).isNotNull();
        String receiptText = text(payments.receipt(paymentId, false).bytes());
        assertThat(receiptText).contains("PAYMENT RECEIPT", String.valueOf(receipt), String.valueOf(inv.summary().number()), "R1 850", "1 000");

        UUID wrong = payments.record(id, new NewPayment(UUID.randomUUID(), 50_000L, LocalDate.now(), PaymentMethod.CASH, null, null)).payments().get(1).id();
        assertThatThrownBy(() -> payments.reject(wrong, new PaymentDecision(0L, " "))).hasMessageContaining("Say why");
        payments.reject(wrong, new PaymentDecision(documents.get(id).payments().get(1).version(), "Not in the bank account"));
        DocumentDetail rest = payments.record(id, new NewPayment(UUID.randomUUID(), 185_000L, LocalDate.now(), PaymentMethod.EFT, "YL balance", null));
        DocumentDetail paid = payments.confirm(rest.payments().get(2).id(), new PaymentDecision(rest.payments().get(2).version(), null));
        assertThat(paid.summary().paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(paid.summary().balanceCents()).isZero();
        assertThat(paid.payments().get(2).receiptNumber()).isEqualTo(receipt + 1);
        assertThatThrownBy(() -> documents.voidDocument(id, new VoidRequest(paid.version(), "Mistake"))).hasMessageContaining("Issue a revision");
        assertThat(paid.events()).extracting(EventView::event).contains("PAYMENT_RECORDED", "PROOF_ATTACHED", "PAYMENT_CONFIRMED", "PAYMENT_REJECTED", "ISSUED");
    }

    @Test void aRevisionReplacesTheOriginalAndCarriesItsPayments() {
        DocumentDetail inv = issued(Kind.INVOICE, BillingFixtures.youngLife());
        UUID id = inv.summary().id();
        DocumentDetail p = payments.record(id, new NewPayment(UUID.randomUUID(), 50_000L, LocalDate.now(), PaymentMethod.EFT, "dep", null));
        payments.confirm(p.payments().get(0).id(), new PaymentDecision(p.payments().get(0).version(), null));
        DocumentDetail revision = documents.revise(id);
        assertThat(documents.revise(id).summary().id()).isEqualTo(revision.summary().id());
        DocumentContent cheaper = content("Sample Client", 40_000);
        DocumentDetail edited = documents.update(revision.summary().id(), new SaveDocument(null, null, revision.version(), null, cheaper, null));
        assertThatThrownBy(() -> documents.issue(edited.summary().id(), edited.version())).hasMessageContaining("already paid on the original");
        DocumentDetail fixed = documents.update(revision.summary().id(), new SaveDocument(null, null, edited.version(), null, content("Sample Client", 300_000), null));
        DocumentDetail issuedRevision = documents.issue(fixed.summary().id(), fixed.version());
        DocumentDetail original = documents.get(id);
        assertThat(original.summary().status()).isEqualTo(Status.VOID);
        assertThat(original.supersededBy().id()).isEqualTo(issuedRevision.summary().id());
        assertThat(original.voidReason()).contains("Replaced by invoice " + issuedRevision.summary().number());
        assertThat(issuedRevision.summary().paidCents()).isEqualTo(50_000L);
        assertThat(issuedRevision.payments()).hasSize(1);
        assertThat(issuedRevision.summary().number()).isNotEqualTo(inv.summary().number());
    }

    @Test void internalNotesNeverReachCustomerDocumentsAndDraftsAreMarked() throws Exception {
        DocumentDetail d = draft(Kind.INVOICE, BillingFixtures.youngLife());
        assertThat(d.internalNotes()).contains("customer pays late");
        String draftText = text(documents.pdf(d.summary().id()).bytes());
        assertThat(draftText).contains("DRAFT").doesNotContain("customer pays late");
        DocumentDetail inv = documents.issue(d.summary().id(), d.version());
        String issuedText = text(documents.pdf(inv.summary().id()).bytes());
        assertThat(issuedText).contains("Drop off:", "Kenilworth", "R2 850", String.valueOf(inv.summary().number())).doesNotContain("Dropp", "customer pays late", "DRAFT");
        assertThat(new String(documents.docx(inv.summary().id()).bytes(), java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("customer pays late");
        assertThatThrownBy(() -> documents.update(inv.summary().id(), new SaveDocument(null, null, inv.version(), null, BillingFixtures.youngLife(), null)))
            .hasMessageContaining("Use Revise");
    }

    @Test void staleEditsAreRefused() {
        DocumentDetail d = draft(Kind.INVOICE, content("Stale", 1_000));
        documents.update(d.summary().id(), new SaveDocument(null, null, d.version(), null, content("Stale", 2_000), null));
        assertThatThrownBy(() -> documents.update(d.summary().id(), new SaveDocument(null, null, d.version(), null, content("Stale", 3_000), null)))
            .isInstanceOf(ConflictException.class);
    }

    @Test void listingFiltersByCustomerStatusAndNumber() {
        DocumentDetail inv = issued(Kind.INVOICE, content("Filterable Safaris", 9_900));
        var byName = documents.list(Kind.INVOICE, null, null, "filterable", null, null, false, 0, 20);
        assertThat(byName.content()).extracting(DocumentSummary::id).contains(inv.summary().id());
        var byNumber = documents.list(null, null, null, String.valueOf(inv.summary().number()), null, null, false, 0, 20);
        assertThat(byNumber.content()).extracting(DocumentSummary::id).containsExactly(inv.summary().id());
        var drafts = documents.list(Kind.INVOICE, Status.DRAFT, null, "filterable", null, null, false, 0, 20);
        assertThat(drafts.content()).isEmpty();
        var dated = documents.list(Kind.INVOICE, null, null, "filterable", LocalDate.now().plusDays(1), null, false, 0, 20);
        assertThat(dated.content()).isEmpty();
    }
}
