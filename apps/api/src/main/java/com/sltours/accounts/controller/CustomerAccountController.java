package com.sltours.accounts.controller;
import com.sltours.accounts.dto.AccountDtos.*;
import com.sltours.accounts.service.CustomerAccountService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** The signed-in customer's own profile and enquiries. Every call is scoped to the authenticated account. */
@RestController
@RequestMapping("/api/v1/account")
public class CustomerAccountController {
    private final CustomerAccountService customers;
    public CustomerAccountController(CustomerAccountService customers) { this.customers = customers; }
    @GetMapping("/profile") Profile profile(Authentication auth) { return customers.profile(auth.getName()); }
    @PutMapping("/profile") Profile update(Authentication auth, @Valid @RequestBody UpdateProfile request) { return customers.updateProfile(auth.getName(), request); }
    @GetMapping("/enquiries") List<CustomerEnquiry> enquiries(Authentication auth) { return customers.enquiries(auth.getName()); }
    @PostMapping("/verification-email") DeliveryResult resend(Authentication auth) { return new DeliveryResult(customers.resendVerification(auth.getName())); }
    @PostMapping("/guest-records") LinkedRecords link(Authentication auth) { return customers.linkGuestRecords(auth.getName()); }
}
