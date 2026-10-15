package com.sltours.accounts.service;

import com.sltours.accounts.model.Account;
import com.sltours.accounts.repository.AccountRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single-use links for email verification and password reset. The emailed token is 256 random bits; only its SHA-256
 * hash is stored. A token is valid once, until it expires, and only while the account still has the address it was sent to.
 */
@Service
public class AccountTokenService {
    public enum Purpose { VERIFY_EMAIL, RESET_PASSWORD }
    public static final Duration VERIFY_LIFETIME = Duration.ofHours(48);
    public static final Duration RESET_LIFETIME = Duration.ofHours(1);

    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final AccountRepository accounts;
    public AccountTokenService(JdbcTemplate jdbc, AccountRepository accounts) { this.jdbc = jdbc; this.accounts = accounts; }

    /** Issues a new token; earlier unused tokens for the same purpose stop working. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String issue(Account account, Purpose purpose) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("update account_tokens set used_at = ? where account_id = ? and purpose = ? and used_at is null", now, account.getId(), purpose.name());
        jdbc.update("insert into account_tokens (id, account_id, purpose, token_hash, email, created_at, expires_at) values (?, ?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(), account.getId(), purpose.name(), hash(token), account.getEmail(), now,
            now.plus(purpose == Purpose.VERIFY_EMAIL ? VERIFY_LIFETIME : RESET_LIFETIME));
        return token;
    }

    /** How many tokens of this purpose were issued to the account since the given time (used for throttling). */
    @Transactional(readOnly = true)
    public int issuedSince(Account account, Purpose purpose, OffsetDateTime since) {
        Integer n = jdbc.queryForObject("select count(*) from account_tokens where account_id = ? and purpose = ? and created_at > ?", Integer.class,
            account.getId(), purpose.name(), since);
        return n == null ? 0 : n;
    }

    /** Marks the token used and returns its enabled account, or empty when it is unknown, used, expired or for an old address. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Account> consume(String token, Purpose purpose) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) return Optional.empty();
        OffsetDateTime now = OffsetDateTime.now();
        List<Map<String, Object>> rows = jdbc.queryForList("""
            update account_tokens set used_at = ? where token_hash = ? and purpose = ? and used_at is null and expires_at > ?
            returning account_id, email""", now, hash(token), purpose.name(), now);
        if (rows.isEmpty()) return Optional.empty();
        UUID accountId = (UUID) rows.get(0).get("account_id");
        String email = (String) rows.get(0).get("email");
        return accounts.findById(accountId).filter(Account::isEnabled).filter(a -> a.getEmail().equals(email));
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
