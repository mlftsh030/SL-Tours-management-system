package com.sltours.reports.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Staff work queues: things that need someone to act. */
public final class ReportDtos {
    private ReportDtos() {}
    public record FollowUp(String reference, UUID customerId, String customerName, String status, String type, OffsetDateTime createdAt,
                           OffsetDateTime updatedAt, long waitingHours) {}
    public record UnassignedBooking(String reference, UUID customerId, String customerName, String title, LocalDate travelDate, int guests, String status) {}
    public record OpenInvoice(UUID id, String number, UUID customerId, String customerName, LocalDate dueDate, String currency, long totalCents,
                              long paidCents, long pendingCents, boolean overdue) {}
    public record PaymentToReview(UUID paymentId, UUID documentId, String invoiceNumber, String customerName, long amountCents, LocalDate paidOn,
                                  String method, String reference, int proofFiles, OffsetDateTime recordedAt) {}
    public record CustomerProof(UUID attachmentId, UUID documentId, String invoiceNumber, String customerName, String uploadedBy, OffsetDateTime uploadedAt) {}
    public record WorkQueues(List<FollowUp> followUps, List<UnassignedBooking> unassignedBookings, List<OpenInvoice> openInvoices,
                             List<PaymentToReview> paymentsToReview, List<CustomerProof> customerProofs, int duplicateGroups) {}
}
