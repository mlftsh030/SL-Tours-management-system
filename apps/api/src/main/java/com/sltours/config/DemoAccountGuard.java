package com.sltours.config;
import com.sltours.accounts.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outside dev/test, disables the demo driver sign-ins that the dev profile creates, in case this database was
 * ever started with the dev profile (for example a promoted staging copy). Their known password must never work.
 */
@Component
@Profile("!dev & !test")
public class DemoAccountGuard implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoAccountGuard.class);
    private final AccountRepository accounts;
    public DemoAccountGuard(AccountRepository accounts) { this.accounts = accounts; }
    @Override @Transactional
    public void run(ApplicationArguments args) {
        for (String email : DevDataBootstrap.DEMO_EMAILS)
            accounts.findByEmail(email).filter(a -> a.isEnabled()).ifPresent(account -> {
                account.setEnabled(false);
                log.warn("Disabled development demo account {} because the dev profile is not active", email);
            });
    }
}
