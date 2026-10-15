package com.sltours.drivers.service;
import com.sltours.accounts.model.Account;
import com.sltours.accounts.repository.AccountRepository;
import com.sltours.accounts.service.AccountService;
import com.sltours.audit.AuditService;
import com.sltours.drivers.dto.DriverDtos.*;
import com.sltours.drivers.model.*;
import com.sltours.drivers.repository.*;
import com.sltours.media.*;
import com.sltours.shared.error.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AdminDriverService {
    static final ZoneId OPERATING_ZONE = ZoneId.of("Africa/Johannesburg");
    private final DriverRepository drivers;
    private final DriverDocumentRepository documents;
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    private final DriverEligibility eligibility;
    private final MediaService media;
    private final AuditService audit;
    private final SessionRegistry sessions;
    private final com.sltours.assignments.service.AdminAssignmentService assignments;
    public AdminDriverService(DriverRepository drivers, DriverDocumentRepository documents, AccountRepository accounts,
            PasswordEncoder passwords, DriverEligibility eligibility, MediaService media, AuditService audit, SessionRegistry sessions,
            com.sltours.assignments.service.AdminAssignmentService assignments) {
        this.drivers = drivers; this.documents = documents; this.accounts = accounts; this.passwords = passwords;
        this.eligibility = eligibility; this.media = media; this.audit = audit; this.sessions = sessions;
        this.assignments = assignments;
    }

    @Transactional(readOnly = true)
    public List<DriverSummary> list() {
        LocalDate today = LocalDate.now(OPERATING_ZONE);
        return drivers.findAllByOrderByNameAsc().stream().map(d -> {
            Optional<Account> account = account(d);
            return new DriverSummary(d.getId(), d.getName(), d.getContact(), account.map(Account::getEmail).orElse(null),
                account.map(Account::isEnabled).orElse(false), d.getStatus(), d.getVerificationStatus(), d.getLicenceExpiresOn(),
                d.getPrdpExpiresOn(), eligibility.problems(d, account.map(Account::isEnabled).orElse(false), today),
                documents.countByDriverIdAndStatus(d.getId(), DriverDocument.Status.PENDING_REVIEW), d.isDemoData());
        }).toList();
    }

    @Transactional(readOnly = true)
    public DriverDetail get(UUID id) { return detail(find(id)); }

    @Transactional
    public DriverDetail create(CreateDriverRequest request) {
        Account account = newAccount(request.email(), request.initialPassword(), request.name());
        Driver driver = new Driver();
        driver.setName(request.name().trim()); driver.setContact(trim(request.contact()));
        driver.setLicenceNumber(trim(request.licenceNumber()));
        driver.setLicenceExpiresOn(request.licenceExpiresOn()); driver.setPrdpExpiresOn(request.prdpExpiresOn());
        driver.setAccountId(account.getId());
        drivers.saveAndFlush(driver);
        audit.record("DRIVER_CREATED", driver.getId().toString(), "account=" + account.getEmail());
        return detail(driver);
    }

    @Transactional
    public DriverDetail createAccount(UUID id, CreateAccountRequest request) {
        Driver driver = find(id);
        if (driver.getAccountId() != null) throw new ConflictException("This driver already has a sign-in account");
        Account account = newAccount(request.email(), request.initialPassword(), driver.getName());
        driver.setAccountId(account.getId());
        drivers.flush();
        audit.record("DRIVER_ACCOUNT_CREATED", id.toString(), "account=" + account.getEmail());
        return detail(driver);
    }

    @Transactional
    public DriverDetail setAccountEnabled(UUID id, boolean enabled) {
        Driver driver = find(id);
        Account account = account(driver).orElseThrow(() -> new IllegalArgumentException("This driver has no sign-in account"));
        account.setEnabled(enabled);
        accounts.flush();
        if (!enabled) expireSessions(account.getEmail());
        audit.record(enabled ? "DRIVER_ACCOUNT_ENABLED" : "DRIVER_ACCOUNT_DISABLED", id.toString(), "account=" + account.getEmail());
        return detail(driver);
    }

    @Transactional
    public DriverDetail update(UUID id, UpdateDriverRequest request) {
        Driver driver = find(id);
        checkVersion(driver.getVersion(), request.version());
        boolean credentialsChanged = !Objects.equals(driver.getLicenceNumber(), trim(request.licenceNumber()))
            || !Objects.equals(driver.getLicenceExpiresOn(), request.licenceExpiresOn())
            || !Objects.equals(driver.getPrdpExpiresOn(), request.prdpExpiresOn());
        driver.setName(request.name().trim()); driver.setContact(trim(request.contact()));
        driver.setStatus(request.status()); driver.setNotes(trim(request.notes()));
        driver.setLicenceNumber(trim(request.licenceNumber()));
        driver.setLicenceExpiresOn(request.licenceExpiresOn()); driver.setPrdpExpiresOn(request.prdpExpiresOn());
        // Changed licence details must be re-verified before the driver is eligible again.
        if (credentialsChanged && driver.getVerificationStatus() == Driver.Verification.VERIFIED)
            driver.setVerification(Driver.Verification.PENDING, "Licence details changed; re-verification required", actor());
        drivers.flush();
        audit.record("DRIVER_UPDATED", id.toString(), "status=" + driver.getStatus() + "; verification=" + driver.getVerificationStatus());
        return detail(driver);
    }

    @Transactional
    public DriverDetail setVerification(UUID id, VerificationRequest request) {
        Driver driver = find(id);
        checkVersion(driver.getVersion(), request.version());
        if (request.status() == Driver.Verification.VERIFIED) requireVerifiable(driver);
        if (request.status() != Driver.Verification.VERIFIED && !StringUtils.hasText(request.notes()))
            throw new IllegalArgumentException("Add a note explaining this verification decision");
        var previous = driver.getVerificationStatus();
        driver.setVerification(request.status(), trim(request.notes()), actor());
        drivers.flush();
        audit.record("DRIVER_VERIFICATION_UPDATED", id.toString(), previous + "->" + request.status());
        return detail(driver);
    }

    @Transactional
    public DriverDetail markGenuine(UUID id) {
        Driver driver = find(id);
        if (!driver.isDemoData()) throw new IllegalArgumentException("This driver is not flagged as sample data");
        driver.setDemoData(false);
        drivers.flush();
        audit.record("SAMPLE_FLAG_REMOVED", id.toString(), "driver confirmed as genuine");
        return detail(driver);
    }

    @Transactional
    public DriverDetail uploadDocument(UUID id, DriverDocument.Type type, LocalDate expiresOn, MultipartFile file) {
        Driver driver = find(id);
        MediaFile stored = media.store(file, MediaPurpose.DRIVER_DOCUMENT, MediaFile.Visibility.PRIVATE, MediaService.DOCUMENTS);
        documents.save(new DriverDocument(driver.getId(), type, stored.getId(), expiresOn, actor()));
        audit.record("DRIVER_DOCUMENT_UPLOADED", id.toString(), type + " media=" + stored.getId());
        return detail(driver);
    }

    @Transactional
    public DriverDetail reviewDocument(UUID driverId, UUID documentId, DocumentReviewRequest request) {
        Driver driver = find(driverId);
        DriverDocument document = documents.findById(documentId).filter(d -> d.getDriverId().equals(driverId))
            .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        checkVersion(document.getVersion(), request.version());
        if (request.status() == DriverDocument.Status.PENDING_REVIEW) throw new IllegalArgumentException("Choose Accepted or Rejected");
        if (request.status() == DriverDocument.Status.REJECTED && !StringUtils.hasText(request.notes()))
            throw new IllegalArgumentException("Add a note explaining why the document was rejected");
        document.review(request.status(), trim(request.notes()), actor());
        documents.flush();
        audit.record("DRIVER_DOCUMENT_REVIEWED", documentId.toString(), document.getType() + "->" + request.status());
        return detail(driver);
    }

    private void requireVerifiable(Driver driver) {
        LocalDate today = LocalDate.now(OPERATING_ZONE);
        List<String> missing = new ArrayList<>();
        if (!StringUtils.hasText(driver.getLicenceNumber())) missing.add("licence number");
        if (driver.getLicenceExpiresOn() == null || driver.getLicenceExpiresOn().isBefore(today)) missing.add("a current licence expiry date");
        if (driver.getPrdpExpiresOn() == null || driver.getPrdpExpiresOn().isBefore(today)) missing.add("a current PrDP expiry date");
        var accepted = documents.findByDriverIdOrderByCreatedAtDesc(driver.getId()).stream()
            .filter(d -> d.getStatus() == DriverDocument.Status.ACCEPTED && (d.getExpiresOn() == null || !d.getExpiresOn().isBefore(today)))
            .map(DriverDocument::getType).toList();
        if (!accepted.contains(DriverDocument.Type.DRIVING_LICENCE)) missing.add("an accepted driving licence document");
        if (!accepted.contains(DriverDocument.Type.PRDP)) missing.add("an accepted PrDP document");
        if (!missing.isEmpty()) throw new IllegalArgumentException("Cannot verify this driver yet. Missing: " + String.join(", ", missing));
    }

    private Account newAccount(String email, String password, String name) {
        String normalized = AccountService.normalizeEmail(email);
        if (accounts.existsByEmail(normalized)) throw new ConflictException("An account with this email already exists");
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) throw new IllegalArgumentException("Password must be at most 72 UTF-8 bytes");
        Account account = new Account();
        account.setEmail(normalized); account.setName(name.trim());
        account.setPasswordHash(passwords.encode(password));
        // Only administrators reach this code path; drivers have no endpoint that changes roles.
        account.setRole(Account.Role.DRIVER);
        return accounts.saveAndFlush(account);
    }

    private void expireSessions(String email) {
        for (Object principal : sessions.getAllPrincipals())
            if (principal instanceof UserDetails user && user.getUsername().equals(email))
                sessions.getAllSessions(principal, false).forEach(org.springframework.security.core.session.SessionInformation::expireNow);
    }

    private DriverDetail detail(Driver d) {
        Optional<Account> account = account(d);
        boolean enabled = account.map(Account::isEnabled).orElse(false);
        var docs = documents.findByDriverIdOrderByCreatedAtDesc(d.getId()).stream().map(doc -> new Document(doc.getId(), doc.getVersion(),
            doc.getType(), doc.getMediaId(), media.find(doc.getMediaId()).getContentType(), doc.getExpiresOn(), doc.getStatus(),
            doc.getReviewNotes(), doc.getReviewedBy(), doc.getReviewedAt(), doc.getCreatedAt())).toList();
        return new DriverDetail(d.getId(), d.getVersion(), d.getName(), d.getContact(), account.map(Account::getEmail).orElse(null), enabled,
            d.getStatus(), d.getNotes(), d.getVerificationStatus(), d.getVerificationNotes(), d.getVerifiedBy(), d.getVerifiedAt(),
            d.getLicenceNumber(), d.getLicenceExpiresOn(), d.getPrdpExpiresOn(),
            eligibility.problems(d, enabled, LocalDate.now(OPERATING_ZONE)), docs, d.isDemoData(), assignments.upcomingForDriver(d.getId()));
    }
    private Optional<Account> account(Driver d) { return d.getAccountId() == null ? Optional.empty() : accounts.findById(d.getAccountId()); }
    private Driver find(UUID id) { return drivers.findById(id).orElseThrow(() -> new ResourceNotFoundException("Driver not found")); }
    private static void checkVersion(long current, long expected) {
        if (current != expected) throw new ConflictException("This record changed. Reload before saving.");
    }
    private static String trim(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private static String actor() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "system" : authentication.getName();
    }
}
