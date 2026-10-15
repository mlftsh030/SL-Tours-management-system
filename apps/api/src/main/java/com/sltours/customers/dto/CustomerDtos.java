package com.sltours.customers.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Staff views of operational customer records (table {@code customers}), separate from login accounts. */
public final class CustomerDtos {
    private CustomerDtos() {}

    public record CustomerSummary(UUID id, String name, String email, String phone, boolean hasAccount, OffsetDateTime lastLoginAt, int activeSessions,
                                  int enquiries, int openEnquiries, int bookings, int upcomingBookings, int invoices, long outstandingCents,
                                  OffsetDateTime lastActivityAt, boolean demoData) {}

    public record EnquiryLine(String reference, String status, String type, String source, String tourTitle, LocalDate requestedDate,
                              OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
    public record BookingLine(String reference, String title, String status, LocalDate travelDate, int guests, long totalCents, long paidCents,
                              boolean assigned, String sourceQuotation) {}
    public record DocumentLine(UUID id, String kind, String status, String displayNumber, LocalDate issueDate, LocalDate dueDate, String currency,
                               long totalCents, long paidCents, String paymentStatus, String bookingReference) {}
    public record PaymentLine(UUID id, UUID documentId, String invoiceNumber, long amountCents, LocalDate paidOn, String method, String status,
                              Long receiptNumber, OffsetDateTime recordedAt) {}
    public record MergeLine(UUID otherId, String otherName, String otherEmail, String direction, String reason, String mergedBy, OffsetDateTime mergedAt) {}

    public record CustomerDetail(CustomerSummary summary, long version, String country, String notes, String accountEmail, boolean accountVerified,
                                 UUID mergedIntoId, List<EnquiryLine> enquiries, List<BookingLine> bookings, List<DocumentLine> documents,
                                 List<PaymentLine> payments, List<MergeLine> merges) {}

    public record UpdateCustomer(@NotNull @Min(0) Long version, @NotBlank @Size(max = 160) String name, @Size(max = 80) String phone,
                                 @Size(max = 80) String country, @Size(max = 4000) String notes) {}

    /** Customers that may be the same person. Staff decide; nothing is merged automatically. */
    public record DuplicateGroup(String reason, String value, List<CustomerSummary> customers) {}

    public record MergeRequest(@NotNull UUID survivorId, @NotNull UUID mergedId, @NotNull @Min(0) Long survivorVersion, @NotNull @Min(0) Long mergedVersion,
                               @NotBlank @Size(max = 500) String reason) {}
}
