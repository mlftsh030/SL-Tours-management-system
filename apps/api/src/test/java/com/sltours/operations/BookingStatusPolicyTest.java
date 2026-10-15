package com.sltours.operations;
import com.sltours.operations.service.BookingStatusPolicy;
import com.sltours.bookings.model.BookingStatus;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class BookingStatusPolicyTest {
    private final BookingStatusPolicy policy = new BookingStatusPolicy();
    @Test void permitsConfirmationAndCancellationButBlocksReopeningTerminalBookings() {
        policy.validate(BookingStatus.ENQUIRY, BookingStatus.CONFIRMED);
        policy.validate(BookingStatus.CONFIRMED, BookingStatus.CANCELLED);
        policy.validate(BookingStatus.CANCELLED, BookingStatus.REFUNDED);
        assertThatThrownBy(() -> policy.validate(BookingStatus.COMPLETED, BookingStatus.ENQUIRY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.validate(BookingStatus.REFUNDED, BookingStatus.CONFIRMED)).isInstanceOf(IllegalArgumentException.class);
    }
}
