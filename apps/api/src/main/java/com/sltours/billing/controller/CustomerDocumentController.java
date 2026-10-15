package com.sltours.billing.controller;
import com.sltours.billing.dto.BillingDtos.CustomerDocument;
import com.sltours.billing.service.CustomerDocumentService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** The signed-in customer's own issued quotations and invoices. */
@RestController
@RequestMapping("/api/v1/account/documents")
public class CustomerDocumentController {
    private final CustomerDocumentService documents;
    public CustomerDocumentController(CustomerDocumentService documents) { this.documents = documents; }

    @GetMapping List<CustomerDocument> list(Authentication authentication) { return documents.list(authentication.getName()); }

    @PostMapping(path = "/{id}/proof", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    CustomerDocument uploadProof(Authentication authentication, @PathVariable UUID id, @RequestPart("file") org.springframework.web.multipart.MultipartFile file) {
        return documents.uploadProof(authentication.getName(), id, file);
    }

    @GetMapping("/{id}/pdf")
    ResponseEntity<byte[]> pdf(Authentication authentication, @PathVariable UUID id) {
        return AdminBillingController.file(documents.pdf(authentication.getName(), id), true);
    }
}
