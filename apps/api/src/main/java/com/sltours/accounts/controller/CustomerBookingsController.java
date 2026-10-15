package com.sltours.accounts.controller;
import com.sltours.accounts.service.CustomerBookingService;
import com.sltours.bookings.dto.BookingResponse;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @Validated
@RequestMapping("/api/v1/account/bookings")
public class CustomerBookingsController {
    private final CustomerBookingService bookings;
    public CustomerBookingsController(CustomerBookingService bookings) { this.bookings = bookings; }
    @GetMapping
    public com.sltours.shared.dto.PageResponse<BookingResponse> list(Authentication authentication,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return com.sltours.shared.dto.PageResponse.from(bookings.list(authentication.getName(), page, size));
    }
}
