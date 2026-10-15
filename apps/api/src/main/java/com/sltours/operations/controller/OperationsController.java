package com.sltours.operations.controller;

import com.sltours.bookings.model.BookingStatus;
import com.sltours.operations.dto.OperationsBookingResponse;
import com.sltours.operations.dto.OperationsDashboardResponse;
import com.sltours.operations.dto.UpdateBookingStatusRequest;
import com.sltours.operations.service.OperationsService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@org.springframework.validation.annotation.Validated
@RequestMapping("/api/v1/admin")
public class OperationsController {
    private final OperationsService operationsService;

    public OperationsController(OperationsService operationsService) {
        this.operationsService = operationsService;
    }

    @GetMapping("/dashboard")
    OperationsDashboardResponse dashboard() {
        return operationsService.dashboard();
    }

    @GetMapping("/bookings")
    com.sltours.shared.dto.PageResponse<OperationsBookingResponse> bookings(@RequestParam(required = false) BookingStatus status,
            @RequestParam(defaultValue = "0") @jakarta.validation.constraints.Min(0) int page,
            @RequestParam(defaultValue = "20") @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(100) int size) {
        return com.sltours.shared.dto.PageResponse.from(operationsService.bookings(status, page, size));
    }

    @PatchMapping("/bookings/{reference}/status")
    OperationsBookingResponse updateStatus(
            @PathVariable String reference,
            @Valid @RequestBody UpdateBookingStatusRequest request
    ) {
        return operationsService.updateStatus(reference, request.status(), request.version());
    }
}
