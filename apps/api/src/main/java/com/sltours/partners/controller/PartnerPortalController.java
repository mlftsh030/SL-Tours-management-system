package com.sltours.partners.controller;
import com.sltours.partners.dto.PartnerDtos.*;
import com.sltours.partners.service.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** The signed-in partner's own records. Every call resolves the partner from the login; ids from other partners are not found. */
@RestController
@RequestMapping("/api/v1/partner")
public class PartnerPortalController {
    private final PartnerPortalService portal;
    private final SupplierProposalService proposals;
    public PartnerPortalController(PartnerPortalService portal, SupplierProposalService proposals) { this.portal = portal; this.proposals = proposals; }

    @GetMapping("/me") PortalProfile me(Authentication auth) { return portal.profile(portal.require(auth)); }
    @GetMapping("/referrals") List<PortalReferral> referrals(Authentication auth) { return portal.referrals(portal.require(auth)); }
    @GetMapping("/statement") PortalStatement statement(Authentication auth) { return portal.statement(portal.require(auth)); }
    @GetMapping("/proposals") List<ProposalView> proposals(Authentication auth) { return proposals.forPartner(portal.require(auth)); }
    @GetMapping("/proposals/{id}") ProposalView proposal(Authentication auth, @PathVariable UUID id) { return proposals.get(portal.require(auth), id); }

    @PostMapping("/proposals")
    ResponseEntity<ProposalView> submit(Authentication auth, @Valid @RequestBody ProposalInput body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(proposals.submit(portal.require(auth), body));
    }
    @PutMapping("/proposals/{id}")
    ProposalView update(Authentication auth, @PathVariable UUID id, @RequestParam long version, @Valid @RequestBody ProposalInput body) {
        return proposals.update(portal.require(auth), id, version, body);
    }
    @PostMapping("/proposals/{id}/withdraw")
    ProposalView withdraw(Authentication auth, @PathVariable UUID id, @RequestParam long version) {
        return proposals.withdraw(portal.require(auth), id, version);
    }
}
