package com.sltours.partners.controller;
import com.sltours.partners.dto.PartnerDtos.*;
import com.sltours.partners.model.PartnerEnums.*;
import com.sltours.partners.service.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Partner administration: decisions, agreements, payouts, supplier payments and proposals. Administrators only. */
@RestController
@RequestMapping("/api/v1/admin/partners")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPartnerController {
    private final PartnerAdminService partners;
    private final PartnerFinanceService finance;
    private final SupplierProposalService proposals;
    public AdminPartnerController(PartnerAdminService partners, PartnerFinanceService finance, SupplierProposalService proposals) {
        this.partners = partners; this.finance = finance; this.proposals = proposals;
    }

    @GetMapping List<PartnerSummary> list(@RequestParam(required = false) RouteStatus status) { return partners.list(status); }
    @GetMapping("/{id}") PartnerDetail detail(@PathVariable UUID id) { return partners.detail(id); }
    @PostMapping("/{id}/decisions") PartnerDetail decide(@PathVariable UUID id, @Valid @RequestBody RouteDecision body) { return partners.decide(id, body); }
    @PostMapping("/{id}/agreements") PartnerDetail agreement(@PathVariable UUID id, @Valid @RequestBody NewAgreement body) { return partners.addAgreement(id, body); }
    @PostMapping("/{id}/agreements/{agreementId}/end")
    PartnerDetail end(@PathVariable UUID id, @PathVariable UUID agreementId, @Valid @RequestBody EndAgreement body) { return partners.endAgreement(id, agreementId, body); }

    @PostMapping("/{id}/payouts")
    PartnerDetail payout(@PathVariable UUID id, @Valid @RequestBody NewPayout body) {
        try { finance.recordPayout(id, body); }
        catch (org.springframework.dao.DuplicateKeyException raced) { /* the same payout was submitted twice at once; the first one stands */ }
        return partners.detail(id);
    }
    @PostMapping("/{id}/supplier-payments")
    PartnerDetail supplierPayment(@PathVariable UUID id, @Valid @RequestBody NewSupplierPayment body) {
        try { finance.recordSupplierPayment(id, body); }
        catch (org.springframework.dao.DuplicateKeyException raced) { /* the first submission stands */ }
        return partners.detail(id);
    }

    @GetMapping("/proposals") List<ProposalView> proposals(@RequestParam(required = false) ProposalStatus status) { return proposals.list(status); }
    @PostMapping("/proposals/{proposalId}/decision")
    ProposalView decideProposal(@PathVariable UUID proposalId, @Valid @RequestBody ProposalDecisionRequest body) { return proposals.decide(proposalId, body); }
}
