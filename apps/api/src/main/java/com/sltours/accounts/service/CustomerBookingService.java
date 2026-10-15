package com.sltours.accounts.service;

import com.sltours.accounts.repository.AccountRepository;
import com.sltours.bookings.repository.BookingRepository;
import com.sltours.bookings.dto.BookingResponse;
import com.sltours.bookings.service.BookingMapper;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerBookingService {
    private final AccountRepository accounts;
    private final BookingRepository bookings;
    private final BookingMapper mapper;
    public CustomerBookingService(AccountRepository accounts, BookingRepository bookings, BookingMapper mapper) {
        this.accounts = accounts; this.bookings = bookings; this.mapper = mapper;
    }
    @Transactional(readOnly = true)
    public Page<BookingResponse> list(String authenticatedEmail, int page, int size) {
        var account = accounts.findByEmail(authenticatedEmail)
            .filter(a -> a.isEnabled() && a.getRole() == com.sltours.accounts.model.Account.Role.CUSTOMER)
            .orElseThrow(() -> new AccessDeniedException("Account unavailable"));
        return bookings.findByAccountId(account.getId(), PageRequest.of(page, size, Sort.by("createdAt").descending()))
            .map(b -> mapper.toResponse(b, "Contact SL Tours for booking assistance"));
    }
}
