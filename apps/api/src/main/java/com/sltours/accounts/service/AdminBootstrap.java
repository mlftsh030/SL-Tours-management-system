package com.sltours.accounts.service;
import com.sltours.accounts.model.Account;
import com.sltours.accounts.repository.AccountRepository;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    private final Environment environment;
    private final String username;
    private final String password;
    public AdminBootstrap(AccountRepository accounts, PasswordEncoder passwords, Environment environment,
            @Value("${sltours.security.admin-username}") String username,
            @Value("${sltours.security.admin-password}") String password) {
        this.accounts = accounts; this.passwords = passwords; this.environment = environment;
        this.username = username; this.password = password;
    }
    @Override @Transactional
    public void run(ApplicationArguments args) {
        if (username.isBlank() || password.getBytes(StandardCharsets.UTF_8).length > 72
                || (!environment.acceptsProfiles(Profiles.of("dev", "test"))
                    && (password.length() < 12 || password.equals("change-me-in-env"))))
            throw new IllegalStateException("Configure a strong admin password; example credentials require the dev profile");
        String email = AccountService.normalizeEmail(username);
        var existing = accounts.findByEmail(email);
        if (existing.isPresent()) {
            if (existing.get().getRole() != Account.Role.ADMIN)
                throw new IllegalStateException("Configured administrator identity conflicts with an existing account");
            // Environment secrets remain the source for the bootstrap administrator password.
            if (!passwords.matches(password, existing.get().getPasswordHash()))
                existing.get().setPasswordHash(passwords.encode(password));
            return;
        }
        Account account = new Account(); account.setEmail(email); account.setName("SL Tours Administrator");
        account.setPasswordHash(passwords.encode(password)); account.setRole(Account.Role.ADMIN); accounts.save(account);
    }
}
