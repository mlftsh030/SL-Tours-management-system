package com.sltours.media;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "media_files")
public class MediaFile {
    public enum Visibility { PRIVATE, PUBLIC }
    @Id private UUID id = UUID.randomUUID();
    @Column(nullable = false, unique = true, length = 200) private String storageKey;
    @Column(nullable = false, length = 80) private String contentType;
    @Column(nullable = false) private long sizeBytes;
    @Column(nullable = false, length = 64) private String sha256;
    @Column(length = 200) private String originalFilename;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private MediaPurpose purpose;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private Visibility visibility;
    @Column(nullable = false, length = 180) private String uploadedBy;
    @Column(nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
    private OffsetDateTime deletedAt;
    @Column(length = 180) private String deletedBy;
    protected MediaFile() {}
    public MediaFile(String storageKey, String contentType, long sizeBytes, String sha256, String originalFilename,
            MediaPurpose purpose, Visibility visibility, String uploadedBy) {
        this.storageKey = storageKey; this.contentType = contentType; this.sizeBytes = sizeBytes; this.sha256 = sha256;
        this.originalFilename = originalFilename; this.purpose = purpose; this.visibility = visibility; this.uploadedBy = uploadedBy;
    }
    public UUID getId() { return id; }
    public String getStorageKey() { return storageKey; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getSha256() { return sha256; }
    public String getOriginalFilename() { return originalFilename; }
    public MediaPurpose getPurpose() { return purpose; }
    public Visibility getVisibility() { return visibility; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public boolean isDeleted() { return deletedAt != null; }
    public void markDeleted(String actor) { deletedAt = OffsetDateTime.now(); deletedBy = actor; }
}
