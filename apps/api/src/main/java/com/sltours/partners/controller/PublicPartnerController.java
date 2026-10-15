package com.sltours.partners.controller;
import com.sltours.partners.dto.PartnerDtos.*;
import com.sltours.partners.service.*;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class PublicPartnerController {
    private final PartnerApplicationService applications;
    private final ReferralService referrals;
    public PublicPartnerController(PartnerApplicationService applications, ReferralService referrals) { this.applications = applications; this.referrals = referrals; }

    @PostMapping("/api/v1/partner-applications")
    ResponseEntity<ApplicationReceipt> apply(@Valid @RequestBody PartnerApplication body) {
        ApplicationReceipt receipt;
        try { receipt = applications.apply(body); }
        catch (DataIntegrityViolationException raced) { receipt = applications.existing(body.idempotencyKey()).orElseThrow(() -> raced); }
        return ResponseEntity.status(receipt.duplicate() ? HttpStatus.OK : HttpStatus.CREATED).body(receipt);
    }

    /** Lets the website name the referring partner (for the disclosure) before a form is submitted. Active partners only. */
    @GetMapping("/api/v1/partners/referrals/{code}")
    ReferralLookup lookup(@PathVariable String code) {
        return referrals.lookup(code).orElseThrow(() -> new com.sltours.shared.error.ResourceNotFoundException("This referral code is not active"));
    }
}
