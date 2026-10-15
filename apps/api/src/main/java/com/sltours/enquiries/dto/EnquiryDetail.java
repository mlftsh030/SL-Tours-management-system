package com.sltours.enquiries.dto;
import com.sltours.enquiries.model.*;
import java.time.*;
import java.util.List;
public record EnquiryDetail(String reference, long version, EnquirySource source, EnquiryType type, EnquiryStatus status,
    String customerName, String customerEmail, String customerPhone, String message, String tourSlug, String tourTitle,
    LocalDate requestedDate, LocalTime requestedTime, Integer durationMinutes, String pickupLocation, String destination,
    Integer passengers, String convertedBookingReference, List<EnquiryStatus> allowedStatuses, List<Note> notes,
    OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    public record Note(String author, String body, OffsetDateTime createdAt) {}
}
