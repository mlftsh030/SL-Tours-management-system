package com.sltours.payments.controller;
import com.sltours.partners.dto.PartnerDtos.*;
import com.sltours.partners.service.ReferralService;
import com.sltours.payments.service.BookingPaymentService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Payments, refunds and referral attribution for one booking. Money and attribution changes are administrator-only. */
@RestController
@RequestMapping("/api/v1/admin/bookings/{reference}")
public class AdminBookingFinanceController {
    private final BookingPaymentService payments;
    private final ReferralService referrals;
    public AdminBookingFinanceController(BookingPaymentService payments, ReferralService referrals) { this.payments = payments; this.referrals = referrals; }

    @GetMapping("/finance")
    BookingFinance finance(@PathVariable String reference) { return payments.finance(reference); }

    @PostMapping("/payments") @PreAuthorize("hasRole('ADMIN')")
    BookingFinance record(@PathVariable String reference, @Valid @RequestBody NewPayment body) {
        try { return payments.record(reference, body); }
        catch (org.springframework.dao.DuplicateKeyException raced) { return payments.finance(reference); } // same key submitted twice at once
    }

    @PostMapping("/commission/evaluate") @PreAuthorize("hasRole('ADMIN')")
    BookingFinance evaluate(@PathVariable String reference) { return payments.reevaluate(reference); }

    @PutMapping("/referral") @PreAuthorize("hasRole('ADMIN')")
    BookingFinance changeReferral(@PathVariable String reference, @Valid @RequestBody ChangeAttribution body) {
        referrals.changeBookingAttribution(reference, body);
        return payments.finance(reference);
    }
}
