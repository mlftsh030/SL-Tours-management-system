package com.sltours.enquiries.controller;
import com.sltours.enquiries.dto.*;
import com.sltours.enquiries.model.*;
import com.sltours.enquiries.service.EnquiryService;
import com.sltours.shared.dto.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** Enquiries inbox for administrators and staff. Drivers and customers are rejected by the security filter chain. */
@RestController @Validated
@RequestMapping("/api/v1/admin/enquiries")
public class AdminEnquiryController {
    private final EnquiryService enquiries;
    public AdminEnquiryController(EnquiryService enquiries) { this.enquiries = enquiries; }
    @GetMapping
    PageResponse<EnquirySummary> list(@RequestParam(required = false) @Size(max = 120) String q,
            @RequestParam(required = false) EnquiryStatus status, @RequestParam(required = false) EnquirySource source,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(enquiries.list(q, status, source, page, size));
    }
    @GetMapping("/{reference}") EnquiryDetail get(@PathVariable String reference) { return enquiries.get(reference); }
    @PatchMapping("/{reference}")
    EnquiryDetail update(@PathVariable String reference, @Valid @RequestBody UpdateEnquiryRequest request) {
        return enquiries.updateStatus(reference, request);
    }
    @PostMapping("/{reference}/notes")
    EnquiryDetail addNote(@PathVariable String reference, @Valid @RequestBody AddEnquiryNoteRequest request) {
        return enquiries.addNote(reference, request);
    }
    @PostMapping("/{reference}/convert")
    ConversionResult convert(@PathVariable String reference, @Valid @RequestBody ConvertEnquiryRequest request) {
        return enquiries.convert(reference, request);
    }
}
