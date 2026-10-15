package com.sltours.accounts.service;
import com.sltours.accounts.dto.*;
import com.sltours.accounts.model.Account;
import com.sltours.accounts.repository.AccountRepository;
import com.sltours.shared.error.ConflictException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AccountService {
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    public AccountService(AccountRepository accounts, PasswordEncoder passwords) {
        this.accounts = accounts; this.passwords = passwords;
    }
    public static String normalizeEmail(String email) { return email.trim().toLowerCase(Locale.ROOT); }

    /** Public sign-up always creates an unverified CUSTOMER; nothing in the request can choose another role. */
    @Transactional
    public Account register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        checkPassword(request.password(), request.passwordConfirmation());
        if (accounts.existsByEmail(email))
            throw new ConflictException("An account with this email already exists. Sign in, or reset your password if you have forgotten it.");
        Account account = new Account();
        account.setEmail(email); account.setName(request.name().trim());
        account.setPhone(StringUtils.hasText(request.phone()) ? request.phone().trim() : null);
        account.setPasswordHash(passwords.encode(request.password())); account.setRole(Account.Role.CUSTOMER);
        return accounts.saveAndFlush(account);
    }
    /** Bcrypt only reads 72 bytes, so longer passwords would be silently truncated. */
    public static void checkPassword(String password, String confirmation) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("Password must be at most 72 UTF-8 bytes");
        if (confirmation != null && !confirmation.equals(password)) throw new IllegalArgumentException("The passwords do not match");
    }
    @Transactional(readOnly = true)
    public SessionResponse session(String email) {
        return accounts.findByEmail(email).filter(Account::isEnabled)
            .map(a -> new SessionResponse(true, a.getEmail(), a.getName(), a.getRole().name(), a.isEmailVerified()))
            .orElseGet(SessionResponse::anonymous);
    }
    @Transactional
    public void recordLogin(String email) {
        accounts.findByEmail(email).ifPresent(a -> a.setLastLoginAt(java.time.OffsetDateTime.now()));
    }
}
