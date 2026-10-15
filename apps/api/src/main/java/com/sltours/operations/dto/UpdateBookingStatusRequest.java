package com.sltours.operations.dto;
import com.sltours.bookings.model.BookingStatus;
import jakarta.validation.constraints.*;
public record UpdateBookingStatusRequest(@NotNull BookingStatus status, @NotNull @Min(0) Long version) {}
