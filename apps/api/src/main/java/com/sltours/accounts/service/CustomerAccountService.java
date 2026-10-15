package com.sltours.accounts.service;

import com.sltours.accounts.dto.AccountDtos.*;
import com.sltours.accounts.model.Account;
import com.sltours.accounts.repository.AccountRepository;
import com.sltours.accounts.service.AccountTokenService.Purpose;
import com.sltours.audit.AuditService;
import com.sltours.enquiries.model.*;
import com.sltours.notifications.EmailSender;
import com.sltours.notifications.EmailSender.Delivery;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * Customer self-service: email verification, password reset, profile and the customer's own enquiries.
 *
 * <p>Guest records (bookings, enquiries and issued documents made without signing in) are linked to an account only after
 * the customer proves they control the email address used on them, by opening the verification link. Registering with a
 * matching address is not enough.
 */
@Service
public class CustomerAccountService {
    private static final DateTimeFormatter EXPIRY = DateTimeFormatter.ofPattern("d MMMM yyyy 'at' HH:mm", Locale.ENGLISH);
    private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");
    private final AccountRepository accounts;
    private final AccountTokenService tokens;
    private final EmailSender email;
    private final PasswordEncoder passwords;
    private final SessionRegistry sessions;
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final TransactionTemplate tx;
    private final String linkBase;

    public CustomerAccountService(AccountRepository accounts, AccountTokenService tokens, EmailSender email, PasswordEncoder passwords,
            SessionRegistry sessions, JdbcTemplate jdbc, AuditService audit, TransactionTemplate tx,
            @Value("${sltours.accounts.link-base-url:${sltours.public-base-url}}") String linkBase) {
        this.accounts = accounts; this.tokens = tokens; this.email = email; this.passwords = passwords; this.sessions = sessions;
        this.jdbc = jdbc; this.audit = audit; this.tx = tx; this.linkBase = linkBase.replaceAll("/+$", "");
    }

    // ---- Email verification ------------------------------------------------------------------------------------

    /** Sends a fresh verification link; at most one a minute and five a day per account. */
    public Delivery sendVerification(UUID accountId) {
        if (!email.isEnabled()) return Delivery.NOT_CONFIGURED;
        record Prepared(String to, String name, String token) {}
        Prepared p = tx.execute(status -> {
            Account a = accounts.findById(accountId).filter(Account::isEnabled).orElseThrow(() -> new AccessDeniedException("Account unavailable"));
            if (a.isEmailVerified()) throw new IllegalArgumentException("Your email address is already verified");
            OffsetDateTime now = OffsetDateTime.now();
            if (tokens.issuedSince(a, Purpose.VERIFY_EMAIL, now.minusMinutes(1)) > 0)
                throw new IllegalArgumentException("We have just sent a verification email. Please wait a minute before asking for another.");
            if (tokens.issuedSince(a, Purpose.VERIFY_EMAIL, now.minusDays(1)) >= 5)
                throw new IllegalArgumentException("Too many verification emails today. Please try again tomorrow or contact SL Tours.");
            return new Prepared(a.getEmail(), a.getName(), tokens.issue(a, Purpose.VERIFY_EMAIL));
        });
        String link = linkBase + "/verify-email?token=" + p.token();
        return email.send(p.to(), "Verify your SL Tours email address", """
            Hello %s,

            Please confirm that this is your email address so we can link your SL Tours bookings, enquiries and invoices to your account:

            %s

            This link works once and expires in 48 hours. If you did not create an SL Tours account, you can ignore this email.

            SL Tours
            """.formatted(p.name(), link));
    }

    public Delivery resendVerification(String signedInEmail) { return sendVerification(customer(signedInEmail).getId()); }

    /** Confirms the address and links guest records that used it. */
    @Transactional
    public VerifyResult verify(String token) {
        Account account = tokens.consume(token, Purpose.VERIFY_EMAIL)
            .orElseThrow(() -> new IllegalArgumentException("This verification link is invalid, already used or expired. Sign in and ask for a new one."));
        if (!account.isEmailVerified()) account.setEmailVerifiedAt(OffsetDateTime.now());
        audit.record("ACCOUNT_EMAIL_VERIFIED", account.getId().toString(), account.getEmail());
        return new VerifyResult(account.getEmail(), account.getRole() == Account.Role.CUSTOMER ? link(account) : new LinkedRecords(0, 0, 0));
    }

    // ---- Guest records -----------------------------------------------------------------------------------------

    /** Links unclaimed guest records made with the customer's verified address. Refused while the address is unverified. */
    @Transactional
    public LinkedRecords linkGuestRecords(String signedInEmail) {
        Account account = customer(signedInEmail);
        if (!account.isEmailVerified()) throw new AccessDeniedException("Verify your email address before linking earlier bookings");
        return link(account);
    }

    private LinkedRecords link(Account account) {
        UUID id = account.getId(); String address = account.getEmail();
        int bookings = jdbc.update("update bookings set account_id = ? where account_id is null and lower(customer_email) = ?", id, address);
        int enquiries = jdbc.update("update enquiries set account_id = ? where account_id is null and lower(customer_email) = ?", id, address);
        // Issued documents not tied to a booking; documents on a booking follow the booking. Drafts stay with staff.
        int documents = jdbc.update("""
            update billing_documents set account_id = ? where account_id is null and booking_id is null and lower(customer_email) = ?
            and status in ('ISSUED', 'ACCEPTED', 'DECLINED')""", id, address);
        // The verified login now belongs to the operational customer record for this address (if it has none yet).
        jdbc.update("""
            update customers set account_id = ?, updated_at = now() where email = ? and merged_into_id is null and account_id is null
            and not exists (select 1 from customers where account_id = ?)""", id, address, id);
        LinkedRecords linked = new LinkedRecords(bookings, enquiries, documents);
        if (linked.total() > 0)
            audit.record("ACCOUNT_GUEST_RECORDS_LINKED", id.toString(), "bookings=" + bookings + " enquiries=" + enquiries + " documents=" + documents);
        return linked;
    }

    // ---- Password reset ----------------------------------------------------------------------------------------

    /**
     * Emails a reset link when a customer account has this address. The answer is the same whether or not it does, so
     * the form cannot be used to find out who has an account. At most three links an hour per account.
     */
    public Delivery requestPasswordReset(String rawEmail) {
        if (!email.isEnabled()) return Delivery.NOT_CONFIGURED;
        String address = AccountService.normalizeEmail(rawEmail);
        record Prepared(String to, String name, String token) {}
        Prepared p = tx.execute(status -> accounts.findByEmail(address).filter(Account::isEnabled)
            .filter(a -> a.getRole() == Account.Role.CUSTOMER)
            .filter(a -> tokens.issuedSince(a, Purpose.RESET_PASSWORD, OffsetDateTime.now().minusHours(1)) < 3)
            .map(a -> new Prepared(a.getEmail(), a.getName(), tokens.issue(a, Purpose.RESET_PASSWORD))).orElse(null));
        if (p == null) return Delivery.SENT;
        String expires = OffsetDateTime.now().plus(AccountTokenService.RESET_LIFETIME).atZoneSameInstant(ZONE).format(EXPIRY);
        Delivery d = email.send(p.to(), "Reset your SL Tours password", """
            Hello %s,

            We received a request to reset the password for your SL Tours account. Choose a new password here:

            %s

            This link works once and expires on %s (South African time). If you did not ask for this, you can ignore this email;
            your password has not changed.

            SL Tours
            """.formatted(p.name(), linkBase + "/reset-password?token=" + p.token(), expires));
        // A delivery failure is reported, but never whether the address has an account.
        return d == Delivery.FAILED ? Delivery.FAILED : Delivery.SENT;
    }

    /** Sets the new password, confirms the address (the link proved control of it) and signs out every existing session. */
    @Transactional
    public void resetPassword(ResetConfirm request) {
        AccountService.checkPassword(request.password(), request.passwordConfirmation());
        Account account = tokens.consume(request.token(), Purpose.RESET_PASSWORD)
            .orElseThrow(() -> new IllegalArgumentException("This reset link is invalid, already used or expired. Ask for a new one."));
        account.setPasswordHash(passwords.encode(request.password()));
        if (!account.isEmailVerified()) account.setEmailVerifiedAt(OffsetDateTime.now());
        expireSessions(account.getEmail());
        audit.record("ACCOUNT_PASSWORD_RESET", account.getId().toString(), account.getEmail());
    }

    private void expireSessions(String username) {
        for (Object principal : sessions.getAllPrincipals())
            if (principal instanceof UserDetails user && user.getUsername().equals(username))
                sessions.getAllSessions(principal, false).forEach(s -> s.expireNow());
    }

    // ---- Profile and enquiries ---------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Profile profile(String signedInEmail) { return profile(customer(signedInEmail)); }

    @Transactional
    public Profile updateProfile(String signedInEmail, UpdateProfile request) {
        Account a = customer(signedInEmail);
        a.setName(request.name().trim());
        a.setPhone(StringUtils.hasText(request.phone()) ? request.phone().trim() : null);
        return profile(a);
    }

    private static Profile profile(Account a) {
        return new Profile(a.getName(), a.getEmail(), a.getPhone(), a.isEmailVerified(), a.getCreatedAt());
    }

    /** The customer's own enquiries. Staff notes and internal fields are never included. */
    @Transactional(readOnly = true)
    public List<CustomerEnquiry> enquiries(String signedInEmail) {
        UUID id = customer(signedInEmail).getId();
        return jdbc.query("""
            select e.reference, e.enquiry_type, e.source, e.status, t.title, e.requested_date, e.passengers, e.message, b.reference as booking_reference, e.created_at
            from enquiries e left join tours t on t.id = e.tour_id left join bookings b on b.id = e.converted_booking_id
            where e.account_id = ? order by e.created_at desc limit 100""", (rs, i) -> new CustomerEnquiry(rs.getString(1),
                EnquiryType.valueOf(rs.getString(2)), EnquirySource.valueOf(rs.getString(3)), EnquiryStatus.valueOf(rs.getString(4)), rs.getString(5),
                rs.getObject(6, java.time.LocalDate.class), (Integer) rs.getObject(7), rs.getString(8), rs.getString(9), rs.getObject(10, OffsetDateTime.class)), id);
    }

    private Account customer(String signedInEmail) {
        return accounts.findByEmail(signedInEmail).filter(a -> a.isEnabled() && a.getRole() == Account.Role.CUSTOMER)
            .orElseThrow(() -> new AccessDeniedException("Account unavailable"));
    }
}
