package com.sltours.operations.service;
import com.sltours.bookings.model.BookingStatus;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
public class BookingStatusPolicy {
    private static final List<BookingStatus> PROGRESS = List.of(BookingStatus.ENQUIRY, BookingStatus.QUOTE_PENDING,
        BookingStatus.QUOTE_SENT, BookingStatus.AWAITING_PAYMENT, BookingStatus.CONFIRMED, BookingStatus.GUIDE_ASSIGNED,
        BookingStatus.READY, BookingStatus.IN_PROGRESS, BookingStatus.COMPLETED);
    public void validate(BookingStatus from, BookingStatus to) {
        if (from == to) return;
        if (to == BookingStatus.REFUNDED && (from == BookingStatus.CANCELLED || from == BookingStatus.COMPLETED)) return;
        if (to == BookingStatus.CANCELLED && PROGRESS.contains(from) && from != BookingStatus.COMPLETED) return;
        if (PROGRESS.contains(from) && PROGRESS.contains(to) && PROGRESS.indexOf(to) > PROGRESS.indexOf(from)) return;
        throw new IllegalArgumentException("This booking status cannot move from " + from + " to " + to);
    }
}
