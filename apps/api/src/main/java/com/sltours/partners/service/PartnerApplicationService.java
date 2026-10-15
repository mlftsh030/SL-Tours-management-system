package com.sltours.partners.service;
import com.sltours.accounts.model.Account;
import com.sltours.accounts.repository.AccountRepository;
import com.sltours.accounts.service.AccountService;
import com.sltours.audit.AuditService;
import com.sltours.partners.dto.PartnerDtos.*;
import com.sltours.partners.model.PartnerEnums.*;
import com.sltours.shared.error.ConflictException;
import com.sltours.shared.util.ReferenceCodes;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Public applications. The applicant chooses a password, but the partner login stays disabled until an administrator
 * approves at least one route. Applications are accepted before any commercial terms exist.
 */
@Service
public class PartnerApplicationService {
    public static final String RECEIVED = "Thank you. Your application has been received. SL Tours will review it and contact you by email.";
    private final JdbcTemplate jdbc;
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    private final AuditService audit;
    public PartnerApplicationService(JdbcTemplate jdbc, AccountRepository accounts, PasswordEncoder passwords, AuditService audit) {
        this.jdbc = jdbc; this.accounts = accounts; this.passwords = passwords; this.audit = audit;
    }

    @Transactional
    public ApplicationReceipt apply(PartnerApplication r) {
        var previous = existing(r.idempotencyKey());
        if (previous.isPresent()) return previous.get();
        if (!r.privacyConsent()) throw new IllegalArgumentException("Confirm that SL Tours may use these details to assess your application");
        if (r.password().getBytes(StandardCharsets.UTF_8).length > 72) throw new IllegalArgumentException("Password must be at most 72 UTF-8 bytes");
        if (r.routes().contains(Route.SUPPLIER) && !StringUtils.hasText(r.servicesOffered()))
            throw new IllegalArgumentException("Describe the services or experiences you would like to offer");
        String email = AccountService.normalizeEmail(r.email());
        if (accounts.findByEmail(email).isPresent() || Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from partners where email = ?)", Boolean.class, email)))
            throw new ConflictException("This email is already registered. Use a different business email, or contact SL Tours.");

        Account account = new Account();
        account.setEmail(email); account.setName(r.contactName().trim()); account.setRole(Account.Role.PARTNER);
        account.setPasswordHash(passwords.encode(r.password()));
        account.setEnabled(false); // enabled on approval
        accounts.saveAndFlush(account);

        UUID id = UUID.randomUUID();
        String reference = ReferenceCodes.next("PTN", 5, ref -> Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from partners where reference = ?)", Boolean.class, ref)));
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
            insert into partners (id, reference, business_name, business_type, contact_name, email, phone, website, area, registration_number, licences,
                description, services_offered, account_id, privacy_consent_at, idempotency_key, created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            id, reference, r.businessName().trim(), r.businessType().name(), r.contactName().trim(), email, trim(r.phone()), trim(r.website()), trim(r.area()),
            trim(r.registrationNumber()), trim(r.licences()), r.description().trim(), trim(r.servicesOffered()), account.getId(), now, r.idempotencyKey(), now, now);
        for (Route route : new TreeSet<>(r.routes())) jdbc.update("insert into partner_routes (partner_id, route, status) values (?, ?, 'PENDING')", id, route.name());
        audit.record("PARTNER_APPLIED", reference, r.businessName().trim() + " " + new TreeSet<>(r.routes()));
        return new ApplicationReceipt(reference, RECEIVED, false);
    }

    @Transactional(readOnly = true)
    public Optional<ApplicationReceipt> existing(UUID key) {
        return jdbc.query("select reference from partners where idempotency_key = ?", (rs, i) -> new ApplicationReceipt(rs.getString(1), RECEIVED, true), key)
            .stream().findFirst();
    }
    private static String trim(String v) { return StringUtils.hasText(v) ? v.trim() : null; }
}
