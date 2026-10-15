package com.sltours.accounts.controller;
import com.sltours.accounts.dto.*;
import com.sltours.accounts.dto.AccountDtos.*;
import com.sltours.accounts.service.AccountService;
import com.sltours.accounts.service.CustomerAccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AccountService accounts;
    private final CustomerAccountService customers;
    public AuthController(AccountService accounts, CustomerAccountService customers) { this.accounts = accounts; this.customers = customers; }
    @GetMapping("/csrf") public CsrfToken csrf(CsrfToken token) { return token; }
    @GetMapping("/me") public SessionResponse session(Authentication authentication) {
        return authentication == null ? SessionResponse.anonymous() : accounts.session(authentication.getName());
    }
    /** Creates a CUSTOMER account and emails a verification link. The account can sign in straight away. */
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public RegisterResult register(@Valid @RequestBody RegisterRequest request) {
        var account = accounts.register(request);
        return new RegisterResult(account.getEmail(), customers.sendVerification(account.getId()));
    }
    @PostMapping("/verify-email")
    public VerifyResult verifyEmail(@Valid @RequestBody TokenRequest request) { return customers.verify(request.token()); }
    @PostMapping("/password-reset/request") @ResponseStatus(HttpStatus.ACCEPTED)
    public DeliveryResult requestPasswordReset(@Valid @RequestBody ResetRequest request) {
        return new DeliveryResult(customers.requestPasswordReset(request.email()));
    }
    @PostMapping("/password-reset/confirm") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetConfirm request) { customers.resetPassword(request); }
}
