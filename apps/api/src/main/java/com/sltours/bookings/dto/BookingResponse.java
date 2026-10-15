package com.sltours.bookings.dto;

import com.sltours.bookings.model.BookingStatus;
import java.time.LocalDate;

public record BookingResponse(
        String reference,
        BookingStatus status,
        String tourTitle,
        LocalDate travelDate,
        int guests,
        int totalAmountCents,
        int amountPaidCents,
        String message
) {
}
