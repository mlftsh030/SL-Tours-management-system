package com.sltours.bookings.controller;

import com.sltours.bookings.dto.BookingResponse;
import com.sltours.bookings.dto.CreateBookingRequest;
import com.sltours.bookings.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {
    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    BookingResponse createBooking(@Valid @RequestBody CreateBookingRequest request, org.springframework.security.core.Authentication authentication) {
        return bookingService.createBooking(request, authentication == null ? null : authentication.getName());
    }
}
