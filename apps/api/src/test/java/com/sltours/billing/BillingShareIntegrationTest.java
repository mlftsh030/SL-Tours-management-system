package com.sltours.billing;
import com.sltours.accounts.model.Account;
import com.sltours.accounts.repository.AccountRepository;
import com.sltours.billing.dto.BillingDtos.*;
import com.sltours.billing.model.BillingTypes.*;
import com.sltours.billing.service.*;
import com.sltours.bookings.model.Booking;
import com.sltours.bookings.repository.BookingRepository;
import com.sltours.shared.error.ResourceNotFoundException;
import com.sltours.support.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

@WithMockUser(username = "admin@test.local", roles = "ADMIN")
class BillingShareIntegrationTest extends PostgresIntegrationTest {
    @Autowired BillingDocumentService documents;
    @Autowired BillingPaymentService payments;
    @Autowired BillingShareService sharing;
    @Autowired BillingSettingsService settings;
    @Autowired NumberAllocator numbers;
    @Autowired CustomerDocumentService customerDocuments;
    @Autowired Fixtures fixtures;
    @Autowired AccountRepository accounts;
    @Autowired BookingRepository bookings;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void confirmNumbering() {
        for (var series : NumberAllocator.Series.values())
            settings.updateSequence(series, new SequenceUpdate(numbers.highestUsed(series) + 1, true));
    }

    DocumentDetail issuedInvoice(String customer, String whatsapp) {
        DocumentContent c = new DocumentContent(new Customer(customer, "Thandi", "thandi@example.com", whatsapp), null, LocalDate.now().plusDays(7),
            null, null, null, null, null, null, null, BillingFixtures.youngLife().lines(), null, false, null, null, null);
        DocumentDetail d = documents.create(new SaveDocument(Kind.INVOICE, UUID.randomUUID(), null, null, c, null));
        return documents.issue(d.summary().id(), d.version());
    }
    static String token(ShareResult r) { return r.url().substring(r.url().lastIndexOf('/') + 1); }
    static HttpStatus status(ResponseStatusException e) { return HttpStatus.valueOf(e.getStatusCode().value()); }

    @Test void whatsappSharePreparesAMessageWithASecureLinkAndRecordsOnlyWhatStaffDid() {
        DocumentDetail inv = issuedInvoice("Sample Client", "082 555 1234");
        ShareResult share = sharing.share(inv.summary().id(), new ShareRequest(Channel.WHATSAPP, 7, null));
        assertThat(token(share)).matches("[A-Za-z0-9_-]{43}");                      // 256-bit random token
        // The number is not the key. (Checking the whole URL for the digits was flaky: a random token can contain them.)
        assertThat(share.url()).endsWith("/" + token(share)).doesNotEndWith("/" + inv.summary().number());
        assertThat(share.whatsappUrl()).startsWith("https://wa.me/27825551234?text=");
        String message = URLDecoder.decode(share.whatsappUrl().substring(share.whatsappUrl().indexOf("text=") + 5), StandardCharsets.UTF_8);
        assertThat(message).contains("Hello Thandi", "invoice " + inv.summary().number(), "Total: R2 850", "Outstanding balance: R2 850",
            "Due date: " + LocalDate.now().plusDays(7).format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy")), share.url(), "proof of payment");
        assertThat(share.mailtoUrl()).startsWith("mailto:thandi@example.com?subject=SL%20Tours%20invoice%20" + inv.summary().number());
        assertThat(jdbc.queryForObject("select count(*) from billing_share_links where token_hash = ?", Integer.class, token(share))).isZero();

        sharing.recordAction(inv.summary().id(), new ShareEvent(ShareAction.WHATSAPP_OPENED, Channel.WHATSAPP, null));
        var events = documents.get(inv.summary().id()).events();
        assertThat(events).extracting(EventView::event).contains("LINK_CREATED", "WHATSAPP_OPENED").noneMatch(e -> e.contains("SENT") || e.contains("DELIVER"));

        BillingShareService.PublicFile file = sharing.open(token(share));
        assertThat(new String(file.bytes(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(file.filename()).isEqualTo("INV-" + inv.summary().number() + ".pdf");
        assertThat(documents.get(inv.summary().id()).links().get(0).openCount()).isEqualTo(1);
    }

    @Test void linksExpireCanBeWithdrawnAndStopWorkingWhenTheDocumentIsVoided() {
        DocumentDetail inv = issuedInvoice("Expiring Ltd", null);
        ShareResult expired = sharing.share(inv.summary().id(), new ShareRequest(Channel.LINK, 1, null));
        assertThat(expired.customerNumberMissing()).isTrue();
        assertThat(expired.whatsappUrl()).startsWith("https://wa.me/?text=");
        jdbc.update("update billing_share_links set expires_at = now() - interval '1 minute' where id = ?", expired.linkId());
        assertThatThrownBy(() -> sharing.open(token(expired))).isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(status(e)).isEqualTo(HttpStatus.GONE)).hasMessageContaining("expired");

        ShareResult withdrawn = sharing.share(inv.summary().id(), new ShareRequest(Channel.EMAIL, 14, null));
        sharing.revoke(inv.summary().id(), withdrawn.linkId());
        assertThatThrownBy(() -> sharing.open(token(withdrawn))).hasMessageContaining("withdrawn");

        ShareResult live = sharing.share(inv.summary().id(), new ShareRequest(Channel.LINK, 14, null));
        assertThat(sharing.open(token(live)).bytes()).isNotEmpty();
        DocumentDetail current = documents.get(inv.summary().id());
        documents.voidDocument(inv.summary().id(), new VoidRequest(current.version(), "Sent to the wrong customer"));
        assertThatThrownBy(() -> sharing.open(token(live))).isInstanceOf(ResponseStatusException.class);

        assertThatThrownBy(() -> sharing.open("not-a-real-token")).isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(status(e)).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> sharing.open("A".repeat(43))).isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(status(e)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test void draftsCannotBeSharedAndReceiptLinksOpenOnlyTheirReceipt() {
        DocumentDetail draft = documents.create(new SaveDocument(Kind.INVOICE, UUID.randomUUID(), null, null, BillingFixtures.youngLife(), null));
        assertThatThrownBy(() -> sharing.share(draft.summary().id(), new ShareRequest(Channel.LINK, 7, null))).hasMessageContaining("Issue the document");

        DocumentDetail inv = issuedInvoice("Receipt Co", "+27 82 111 2222");
        DocumentDetail p = payments.record(inv.summary().id(), new NewPayment(UUID.randomUUID(), 85_000L, LocalDate.now(), PaymentMethod.EFT, "R1", null));
        UUID paymentId = p.payments().get(0).id();
        assertThatThrownBy(() -> sharing.share(inv.summary().id(), new ShareRequest(Channel.WHATSAPP, 7, paymentId))).hasMessageContaining("once the payment is confirmed");
        DocumentDetail confirmed = payments.confirm(paymentId, new PaymentDecision(p.payments().get(0).version(), null));
        ShareResult receipt = sharing.share(inv.summary().id(), new ShareRequest(Channel.WHATSAPP, 7, paymentId));
        assertThat(receipt.whatsappUrl()).startsWith("https://wa.me/27821112222?text=");
        assertThat(URLDecoder.decode(receipt.whatsappUrl(), StandardCharsets.UTF_8)).contains("payment of R850", "Outstanding balance: R2 000");
        assertThat(sharing.open(token(receipt)).filename()).isEqualTo("REC-" + confirmed.payments().get(0).receiptNumber() + ".pdf");
    }

    @Test void customersSeeOnlyTheirOwnIssuedDocuments() {
        Account mine = customer("owner"), other = customer("other");
        Booking booking = fixtures.tourBooking(2, LocalDate.now().plusDays(30));
        jdbc.update("update bookings set account_id = ? where id = ?", mine.getId(), booking.getId());
        BookingPrefill prefill = documents.prefill(booking.getReference());
        DocumentDetail d = documents.create(new SaveDocument(Kind.INVOICE, UUID.randomUUID(), null, booking.getReference(), prefill.content(), "Owner owes"));
        assertThat(customerDocuments.list(mine.getEmail())).isEmpty();             // drafts are never shown
        DocumentDetail inv = documents.issue(d.summary().id(), d.version());
        assertThat(customerDocuments.list(mine.getEmail())).extracting(CustomerDocument::id).containsExactly(inv.summary().id());
        assertThat(customerDocuments.pdf(mine.getEmail(), inv.summary().id()).bytes()).isNotEmpty();
        assertThat(customerDocuments.list(other.getEmail())).isEmpty();
        assertThatThrownBy(() -> customerDocuments.pdf(other.getEmail(), inv.summary().id())).isInstanceOf(ResourceNotFoundException.class);
    }

    Account customer(String name) {
        Account a = new Account();
        a.setEmail(name + "-" + Fixtures.next() + "@example.com"); a.setName(name); a.setRole(Account.Role.CUSTOMER);
        a.setPasswordHash("{noop}x");
        return accounts.saveAndFlush(a);
    }
}
