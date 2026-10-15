package com.sltours.drivers.controller;
import com.sltours.drivers.dto.DriverDtos.*;
import com.sltours.drivers.model.DriverDocument;
import com.sltours.drivers.service.AdminDriverService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Driver records, accounts and verification are administrator-only; staff cannot read verification data. */
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/v1/admin/drivers")
public class AdminDriverController {
    private final AdminDriverService drivers;
    public AdminDriverController(AdminDriverService drivers) { this.drivers = drivers; }
    @GetMapping List<DriverSummary> list() { return drivers.list(); }
    @GetMapping("/{id}") DriverDetail get(@PathVariable UUID id) { return drivers.get(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) DriverDetail create(@Valid @RequestBody CreateDriverRequest request) { return drivers.create(request); }
    @PutMapping("/{id}") DriverDetail update(@PathVariable UUID id, @Valid @RequestBody UpdateDriverRequest request) { return drivers.update(id, request); }
    @PostMapping("/{id}/account") DriverDetail createAccount(@PathVariable UUID id, @Valid @RequestBody CreateAccountRequest request) {
        return drivers.createAccount(id, request);
    }
    @PatchMapping("/{id}/account") DriverDetail accountStatus(@PathVariable UUID id, @RequestBody AccountStatusRequest request) {
        return drivers.setAccountEnabled(id, request.enabled());
    }
    @PostMapping("/{id}/genuine") DriverDetail markGenuine(@PathVariable UUID id) { return drivers.markGenuine(id); }
    @PatchMapping("/{id}/verification") DriverDetail verify(@PathVariable UUID id, @Valid @RequestBody VerificationRequest request) {
        return drivers.setVerification(id, request);
    }
    @PostMapping(path = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    DriverDetail upload(@PathVariable UUID id, @RequestParam DriverDocument.Type type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiresOn,
            @RequestPart("file") MultipartFile file) {
        return drivers.uploadDocument(id, type, expiresOn, file);
    }
    @PatchMapping("/{id}/documents/{documentId}") DriverDetail review(@PathVariable UUID id, @PathVariable UUID documentId,
            @Valid @RequestBody DocumentReviewRequest request) {
        return drivers.reviewDocument(id, documentId, request);
    }
}
