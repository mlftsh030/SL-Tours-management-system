package com.sltours.accounts;

import com.sltours.accounts.model.Account;
import com.sltours.accounts.repository.AccountRepository;
import com.sltours.accounts.service.CustomerBookingService;
import com.sltours.bookings.repository.BookingRepository;
import com.sltours.bookings.service.BookingMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;

class CustomerBookingServiceTest {
    @Test void historyUsesAuthenticatedAccountIdRatherThanEmailOrCustomerSuppliedId() {
        var accounts = mock(AccountRepository.class); var bookings = mock(BookingRepository.class);
        var account = new Account(); account.setEmail("a@example.com"); account.setRole(Account.Role.CUSTOMER);
        when(accounts.findByEmail("a@example.com")).thenReturn(Optional.of(account));
        when(bookings.findByAccountId(eq(account.getId()), any())).thenReturn(Page.empty());
        var service = new CustomerBookingService(accounts, bookings, new BookingMapper());
        assertThat(service.list("a@example.com", 0, 20).getContent()).isEmpty();
        verify(bookings).findByAccountId(eq(account.getId()), any());
        verifyNoMoreInteractions(bookings);
    }
    @Test void staffAccountCannotReadCustomerHistoryThroughService() {
        var accounts = mock(AccountRepository.class); var bookings = mock(BookingRepository.class);
        var account = new Account(); account.setRole(Account.Role.STAFF);
        when(accounts.findByEmail("staff@example.com")).thenReturn(Optional.of(account));
        var service = new CustomerBookingService(accounts, bookings, new BookingMapper());
        assertThatThrownBy(() -> service.list("staff@example.com", 0, 20))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(bookings);
    }
}
