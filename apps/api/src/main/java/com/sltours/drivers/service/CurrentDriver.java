package com.sltours.drivers.service;
import com.sltours.accounts.model.Account;
import com.sltours.accounts.repository.AccountRepository;
import com.sltours.drivers.model.Driver;
import com.sltours.drivers.repository.DriverRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Resolves the driver profile for the signed-in account. Every driver endpoint scopes its data through this. */
@Component
public class CurrentDriver {
    private final AccountRepository accounts;
    private final DriverRepository drivers;
    public CurrentDriver(AccountRepository accounts, DriverRepository drivers) { this.accounts = accounts; this.drivers = drivers; }
    @Transactional(readOnly = true)
    public Driver require(Authentication authentication) {
        if (authentication == null) throw new AccessDeniedException("Driver access required");
        Account account = accounts.findByEmail(authentication.getName())
            .filter(a -> a.isEnabled() && a.getRole() == Account.Role.DRIVER)
            .orElseThrow(() -> new AccessDeniedException("Driver access required"));
        return drivers.findByAccountId(account.getId()).orElseThrow(() -> new AccessDeniedException("No driver profile is linked to this account"));
    }
}
