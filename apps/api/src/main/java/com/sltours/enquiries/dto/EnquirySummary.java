package com.sltours.enquiries.dto;
import com.sltours.enquiries.model.*;
import java.time.*;
public record EnquirySummary(String reference, EnquirySource source, EnquiryType type, EnquiryStatus status,
    String customerName, String customerEmail, String customerPhone, String messagePreview, String tourTitle,
    LocalDate requestedDate, Integer passengers, OffsetDateTime createdAt, boolean demoData) {}
