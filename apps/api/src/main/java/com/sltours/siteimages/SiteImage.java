package com.sltours.siteimages;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "site_images")
public class SiteImage {
    public enum Slot { HOME_HERO, GALLERY }
    @Id private UUID id = UUID.randomUUID();
    @Version private Long version;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private Slot slot;
    @Column(nullable = false, unique = true) private UUID mediaId;
    @Column(nullable = false, length = 300) private String altText;
    @Column(length = 160) private String title;
    @Column(length = 160) private String subtitle;
    @Column(length = 300) private String body;
    @Column(nullable = false) private int sortOrder;
    @Column(nullable = false) private boolean active;
    @Column(nullable = false, length = 180) private String createdBy;
    @Column(nullable = false) private OffsetDateTime createdAt;
    @Column(nullable = false) private OffsetDateTime updatedAt;
    @PrePersist void onCreate() { createdAt = OffsetDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void onUpdate() { updatedAt = OffsetDateTime.now(); }
    protected SiteImage() {}
    public SiteImage(Slot slot, UUID mediaId, String createdBy) { this.slot = slot; this.mediaId = mediaId; this.createdBy = createdBy; }
    public UUID getId() { return id; }
    public long getVersion() { return version == null ? 0 : version; }
    public Slot getSlot() { return slot; }
    public UUID getMediaId() { return mediaId; }
    public String getAltText() { return altText; }
    public String getTitle() { return title; }
    public String getSubtitle() { return subtitle; }
    public String getBody() { return body; }
    public int getSortOrder() { return sortOrder; }
    public boolean isActive() { return active; }
    public void update(String altText, String title, String subtitle, String body, int sortOrder, boolean active) {
        this.altText = altText; this.title = title; this.subtitle = subtitle; this.body = body; this.sortOrder = sortOrder; this.active = active;
    }
}
