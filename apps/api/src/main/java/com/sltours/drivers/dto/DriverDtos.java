package com.sltours.drivers.dto;
import com.sltours.drivers.model.*;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class DriverDtos {
    private DriverDtos() {}
    public record DriverSummary(UUID id, String name, String contact, String email, boolean accountEnabled, Driver.Status status,
        Driver.Verification verificationStatus, LocalDate licenceExpiresOn, LocalDate prdpExpiresOn, List<String> eligibilityProblems,
        long documentsAwaitingReview, boolean demoData) {}
    public record DriverDetail(UUID id, long version, String name, String contact, String email, boolean accountEnabled,
        Driver.Status status, String notes, Driver.Verification verificationStatus, String verificationNotes, String verifiedBy,
        OffsetDateTime verifiedAt, String licenceNumber, LocalDate licenceExpiresOn, LocalDate prdpExpiresOn,
        List<String> eligibilityProblems, List<Document> documents, boolean demoData,
        List<com.sltours.assignments.dto.AssignmentDtos.AdminAssignment> upcomingAssignments) {}
    public record Document(UUID id, long version, DriverDocument.Type type, UUID mediaId, String contentType, LocalDate expiresOn,
        DriverDocument.Status status, String reviewNotes, String reviewedBy, OffsetDateTime reviewedAt, OffsetDateTime createdAt) {}
    public record CreateDriverRequest(@NotBlank @Size(max = 160) String name, @NotBlank @Email @Size(max = 180) String email,
        @Size(max = 120) String contact, @NotBlank @Size(min = 12, max = 64) String initialPassword,
        @Size(max = 40) String licenceNumber, LocalDate licenceExpiresOn, LocalDate prdpExpiresOn) {}
    public record UpdateDriverRequest(@NotNull @Min(0) Long version, @NotBlank @Size(max = 160) String name,
        @Size(max = 120) String contact, @NotNull Driver.Status status, @Size(max = 2000) String notes,
        @Size(max = 40) String licenceNumber, LocalDate licenceExpiresOn, LocalDate prdpExpiresOn) {}
    public record CreateAccountRequest(@NotBlank @Email @Size(max = 180) String email, @NotBlank @Size(min = 12, max = 64) String initialPassword) {}
    public record AccountStatusRequest(boolean enabled) {}
    public record VerificationRequest(@NotNull @Min(0) Long version, @NotNull Driver.Verification status, @Size(max = 1000) String notes) {}
    public record DocumentReviewRequest(@NotNull @Min(0) Long version, @NotNull DriverDocument.Status status, @Size(max = 1000) String notes) {}
    public record DriverProfile(String name, Driver.Verification verificationStatus, LocalDate licenceExpiresOn,
        LocalDate prdpExpiresOn, List<String> eligibilityProblemsToday) {}
}
