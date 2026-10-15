package com.sltours.config;
import com.sltours.accounts.model.Account;
import com.sltours.accounts.repository.AccountRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DemoAccountGuardTest {
    @Test void demoDriverSignInsAreDisabledOutsideDevelopment() {
        AccountRepository accounts = mock(AccountRepository.class);
        Account demo = new Account(); demo.setEmail("sipho.driver@sltours.local"); demo.setRole(Account.Role.DRIVER);
        when(accounts.findByEmail(anyString())).thenReturn(Optional.empty());
        when(accounts.findByEmail("sipho.driver@sltours.local")).thenReturn(Optional.of(demo));
        new DemoAccountGuard(accounts).run(null);
        assertThat(demo.isEnabled()).isFalse();
        assertThat(DevDataBootstrap.DEMO_EMAILS).containsExactlyInAnyOrder("sipho.driver@sltours.local", "daniel.driver@sltours.local");
    }
}
