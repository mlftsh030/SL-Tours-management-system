package com.sltours.activities.model;
import com.sltours.activities.model.ActivityEnums.*;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

/** A picture or video of an activity, with its credit, licence and whether it shows the exact place. */
@Entity
@Table(name = "activity_media")
public class ActivityMedia {
    @Id private UUID id = UUID.randomUUID();
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "activity_id") private Activity activity;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private MediaKind kind;
    @Column(nullable = false, length = 500) private String url;
    @Column(length = 500) private String thumbnailUrl;
    private Integer width;
    private Integer height;
    @Column(nullable = false, length = 300) private String altText;
    @Column(length = 300) private String caption;
    @Column(nullable = false, length = 300) private String credit;
    @Column(nullable = false, length = 500) private String sourcePage;
    @Column(nullable = false, length = 120) private String licence;
    @Column(length = 500) private String licenceUrl;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private MediaPermission permission;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Depiction depiction;
    @Column(nullable = false) private LocalDate checkedOn;
    @Column(nullable = false) private int sortOrder;
    protected ActivityMedia() {}
    public ActivityMedia(Activity activity) { this.activity = activity; }
    public void set(MediaKind kind, String url, String thumbnailUrl, Integer width, Integer height, String altText, String caption, String credit,
            String sourcePage, String licence, String licenceUrl, MediaPermission permission, Depiction depiction, LocalDate checkedOn, int sortOrder) {
        this.kind = kind; this.url = url; this.thumbnailUrl = thumbnailUrl; this.width = width; this.height = height; this.altText = altText;
        this.caption = caption; this.credit = credit; this.sourcePage = sourcePage; this.licence = licence; this.licenceUrl = licenceUrl;
        this.permission = permission; this.depiction = depiction; this.checkedOn = checkedOn; this.sortOrder = sortOrder;
    }
    /** Shown publicly only with a confirmed permission. */
    public boolean isPublic() { return permission != MediaPermission.PENDING; }
    public MediaKind getKind() { return kind; }
    public String getUrl() { return url; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public Integer getWidth() { return width; }
    public Integer getHeight() { return height; }
    public String getAltText() { return altText; }
    public String getCaption() { return caption; }
    public String getCredit() { return credit; }
    public String getSourcePage() { return sourcePage; }
    public String getLicence() { return licence; }
    public String getLicenceUrl() { return licenceUrl; }
    public MediaPermission getPermission() { return permission; }
    public Depiction getDepiction() { return depiction; }
    public LocalDate getCheckedOn() { return checkedOn; }
    public int getSortOrder() { return sortOrder; }
}
