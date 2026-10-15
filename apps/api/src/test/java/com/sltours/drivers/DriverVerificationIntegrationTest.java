package com.sltours.drivers;
import com.sltours.accounts.model.Account;
import com.sltours.accounts.repository.AccountRepository;
import com.sltours.drivers.dto.DriverDtos.*;
import com.sltours.drivers.model.*;
import com.sltours.drivers.service.AdminDriverService;
import com.sltours.shared.error.ConflictException;
import com.sltours.support.PostgresIntegrationTest;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import static org.assertj.core.api.Assertions.*;

@WithMockUser(username = "admin@test.local", roles = "ADMIN")
class DriverVerificationIntegrationTest extends PostgresIntegrationTest {
    @Autowired AdminDriverService drivers;
    @Autowired AccountRepository accounts;
    private static MockMultipartFile pdf() { return new MockMultipartFile("file", "doc.pdf", "application/pdf", "%PDF-1.7 test".getBytes()); }

    @Test void verificationNeedsAcceptedDocumentsAndCreatesOnlyDriverAccounts() {
        String email = "new.driver." + UUID.randomUUID().toString().substring(0, 6) + "@test.local";
        DriverDetail created = drivers.create(new CreateDriverRequest("Nomsa Dlamini", email, "+27 72 000 0000", "a-long-initial-password",
            "SA-123", LocalDate.now().plusYears(2), LocalDate.now().plusYears(1)));
        assertThat(accounts.findByEmail(email)).get().extracting(Account::getRole).isEqualTo(Account.Role.DRIVER);
        assertThat(created.eligibilityProblems()).contains("Driver verification is not complete");
        assertThatThrownBy(() -> drivers.create(new CreateDriverRequest("Copy", email, null, "a-long-initial-password", null, null, null)))
            .isInstanceOf(ConflictException.class);

        assertThatThrownBy(() -> drivers.setVerification(created.id(), new VerificationRequest(created.version(), Driver.Verification.VERIFIED, null)))
            .hasMessageContaining("an accepted driving licence document");

        DriverDetail withDocs = drivers.uploadDocument(created.id(), DriverDocument.Type.DRIVING_LICENCE, LocalDate.now().plusYears(2), pdf());
        withDocs = drivers.uploadDocument(created.id(), DriverDocument.Type.PRDP, LocalDate.now().plusYears(1), pdf());
        assertThatThrownBy(() -> drivers.uploadDocument(created.id(), DriverDocument.Type.OTHER, null,
            new MockMultipartFile("file", "x.exe", "application/pdf", "MZ not a pdf".getBytes()))).hasMessageContaining("JPEG, PNG, WebP or PDF");
        for (Document doc : withDocs.documents())
            withDocs = drivers.reviewDocument(created.id(), doc.id(), new DocumentReviewRequest(doc.version(), DriverDocument.Status.ACCEPTED, null));
        DriverDetail verified = drivers.setVerification(created.id(), new VerificationRequest(withDocs.version(), Driver.Verification.VERIFIED, null));
        assertThat(verified.eligibilityProblems()).isEmpty();

        // Changing licence details requires re-verification.
        DriverDetail changed = drivers.update(created.id(), new UpdateDriverRequest(verified.version(), "Nomsa Dlamini", "+27 72 000 0000",
            Driver.Status.ACTIVE, null, "SA-999", LocalDate.now().plusYears(2), LocalDate.now().plusYears(1)));
        assertThat(changed.verificationStatus()).isEqualTo(Driver.Verification.PENDING);

        DriverDetail disabled = drivers.setAccountEnabled(created.id(), false);
        assertThat(disabled.eligibilityProblems()).contains("Driver account is disabled");
    }
}
