package com.sltours.customers.controller;

import com.sltours.customers.dto.CustomerDtos.*;
import com.sltours.customers.service.CustomerRecordsService;
import com.sltours.shared.dto.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** Customer records for staff. Searching and viewing are open to staff; merging is for administrators. */
@RestController @Validated
@RequestMapping("/api/v1/admin/customers")
public class AdminCustomerController {
    private final CustomerRecordsService customers;
    public AdminCustomerController(CustomerRecordsService customers) { this.customers = customers; }

    @GetMapping PageResponse<CustomerSummary> search(@RequestParam(required = false) @Size(max = 120) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return customers.search(q, page, size);
    }
    @GetMapping("/duplicates") List<DuplicateGroup> duplicates() { return customers.duplicates(); }
    @GetMapping("/{id}") CustomerDetail detail(@PathVariable UUID id) { return customers.detail(id); }
    @PutMapping("/{id}") CustomerDetail update(@PathVariable UUID id, @Valid @RequestBody UpdateCustomer body) { return customers.update(id, body); }
    @PostMapping("/merge") @PreAuthorize("hasRole('ADMIN')") CustomerDetail merge(@Valid @RequestBody MergeRequest body) { return customers.merge(body); }
}
