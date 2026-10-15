package com.sltours.drivers.model;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

/** Private verification document. The file itself is a media_files row served only to administrators. */
@Entity
@Table(name = "driver_documents")
public class DriverDocument {
    public enum Type { DRIVING_LICENCE, PRDP, IDENTITY_DOCUMENT, OTHER }
    public enum Status { PENDING_REVIEW, ACCEPTED, REJECTED }
    @Id private UUID id = UUID.randomUUID();
    @Version private Long version;
    @Column(nullable = false) private UUID driverId;
    @Enumerated(EnumType.STRING) @Column(name = "document_type", nullable = false, length = 30) private Type type;
    @Column(nullable = false, unique = true) private UUID mediaId;
    private LocalDate expiresOn;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status = Status.PENDING_REVIEW;
    @Column(length = 1000) private String reviewNotes;
    @Column(length = 180) private String reviewedBy;
    private OffsetDateTime reviewedAt;
    @Column(nullable = false, length = 180) private String uploadedBy;
    @Column(nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
    protected DriverDocument() {}
    public DriverDocument(UUID driverId, Type type, UUID mediaId, LocalDate expiresOn, String uploadedBy) {
        this.driverId = driverId; this.type = type; this.mediaId = mediaId; this.expiresOn = expiresOn; this.uploadedBy = uploadedBy;
    }
    public UUID getId() { return id; }
    public long getVersion() { return version == null ? 0 : version; }
    public UUID getDriverId() { return driverId; }
    public Type getType() { return type; }
    public UUID getMediaId() { return mediaId; }
    public LocalDate getExpiresOn() { return expiresOn; }
    public Status getStatus() { return status; }
    public String getReviewNotes() { return reviewNotes; }
    public String getReviewedBy() { return reviewedBy; }
    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void review(Status status, String notes, String actor) {
        this.status = status; reviewNotes = notes; reviewedBy = actor; reviewedAt = OffsetDateTime.now();
    }
}
