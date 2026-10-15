package com.sltours.operations.dto;
import com.sltours.bookings.model.BookingStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
public record OperationsBookingResponse(
    String reference, String guest, String tour, LocalDate travelDate, int guests,
    int totalAmountCents, int amountPaidCents, BookingStatus status, String guide, String vehicle,
    String email, String phone, String country, String pickupLocation, String notes,
    boolean registeredCustomer, OffsetDateTime createdAt, long version, String bookingType, String driver, String assignmentStatus, boolean demoData
) {}
