package com.sltours.accounts;
import com.sltours.accounts.dto.RegisterRequest;
import com.sltours.accounts.model.Account;
import com.sltours.accounts.repository.AccountRepository;
import com.sltours.accounts.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
class AccountServiceTest {
    @Test void registrationHashesPasswordAndAlwaysCreatesCustomerRole() {
        var accounts = mock(AccountRepository.class); var passwords = new BCryptPasswordEncoder(4);
        var service = new AccountService(accounts, passwords);
        service.register(new RegisterRequest(" Customer ", " PERSON@EXAMPLE.COM ", "strong-password-123"));
        var captor = org.mockito.ArgumentCaptor.forClass(Account.class);
        verify(accounts).saveAndFlush(captor.capture());
        Account account = captor.getValue();
        assertThat(account.getRole()).isEqualTo(Account.Role.CUSTOMER);
        assertThat(account.getEmail()).isEqualTo("person@example.com");
        assertThat(account.getPasswordHash()).isNotEqualTo("strong-password-123");
        assertThat(passwords.matches("strong-password-123", account.getPasswordHash())).isTrue();
    }
    @Test void bcryptByteLimitCannotSilentlyTruncateUnicodePassword() {
        var accounts = mock(AccountRepository.class); var service = new AccountService(accounts, new BCryptPasswordEncoder(4));
        assertThatThrownBy(() -> service.register(new RegisterRequest("Name", "a@example.com", "😀".repeat(20))))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("72 UTF-8 bytes");
        verify(accounts, never()).saveAndFlush(any());
    }
}
