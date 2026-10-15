package com.sltours.enquiries.controller;
import com.sltours.enquiries.dto.*;
import com.sltours.enquiries.service.EnquiryService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/enquiries")
public class EnquiryController {
    private final EnquiryService enquiries;
    public EnquiryController(EnquiryService enquiries) { this.enquiries = enquiries; }

    @PostMapping
    ResponseEntity<EnquiryReceipt> submit(@Valid @RequestBody CreateEnquiryRequest request) {
        EnquiryReceipt receipt;
        try {
            receipt = enquiries.submit(request);
        } catch (DataIntegrityViolationException raced) {
            // Two identical submissions raced; the first one stored wins and both callers get its receipt.
            receipt = enquiries.existing(request.idempotencyKey()).orElseThrow(() -> raced);
        }
        return ResponseEntity.status(receipt.duplicate() ? HttpStatus.OK : HttpStatus.CREATED).body(receipt);
    }
}
