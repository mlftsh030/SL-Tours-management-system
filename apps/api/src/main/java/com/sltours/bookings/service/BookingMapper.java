package com.sltours.bookings.service;

import com.sltours.bookings.dto.BookingResponse;
import com.sltours.bookings.model.Booking;
import org.springframework.stereotype.Component;

@Component
public class BookingMapper {
    public BookingResponse toResponse(Booking booking, String message) {
        return new BookingResponse(
                booking.getReference(),
                booking.getStatus(),
                booking.getTitle(),
                booking.getTravelDate(),
                booking.getGuests(),
                booking.getTotalAmountCents(),
                booking.getAmountPaidCents(),
                message);
    }
}
