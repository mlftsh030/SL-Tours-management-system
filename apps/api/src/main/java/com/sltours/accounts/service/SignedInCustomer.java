package com.sltours.accounts.service;
import com.sltours.accounts.model.Account;
import com.sltours.accounts.repository.AccountRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** The enabled CUSTOMER account behind the current request, if any. Staff, drivers and partners are not customers. */
@Component
public class SignedInCustomer {
    private final AccountRepository accounts;
    public SignedInCustomer(AccountRepository accounts) { this.accounts = accounts; }
    public Optional<UUID> id() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) return Optional.empty();
        if (auth.getAuthorities().stream().noneMatch(a -> "ROLE_CUSTOMER".equals(a.getAuthority()))) return Optional.empty();
        return accounts.findByEmail(auth.getName()).filter(a -> a.isEnabled() && a.getRole() == Account.Role.CUSTOMER).map(Account::getId);
    }
}
