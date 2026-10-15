package com.sltours.activities.model;
import com.sltours.activities.model.ActivityEnums.SourceType;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "activity_sources")
public class ActivitySource {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "activity_id") private Activity activity;
    @Column(nullable = false, length = 500) private String url;
    @Column(nullable = false, length = 160) private String publisher;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private SourceType sourceType;
    @Column(nullable = false, length = 300) private String covers;
    @Column(nullable = false) private LocalDate accessedOn;
    @Column(nullable = false) private int sortOrder;
    protected ActivitySource() {}
    public ActivitySource(UUID id, Activity activity, String url, String publisher, SourceType type, String covers, LocalDate accessedOn, int sortOrder) {
        this.id = id; this.activity = activity; this.url = url; this.publisher = publisher; this.sourceType = type; this.covers = covers;
        this.accessedOn = accessedOn; this.sortOrder = sortOrder;
    }
    public UUID getId() { return id; }
    public String getUrl() { return url; }
    public String getPublisher() { return publisher; }
    public SourceType getSourceType() { return sourceType; }
    public String getCovers() { return covers; }
    public LocalDate getAccessedOn() { return accessedOn; }
    public int getSortOrder() { return sortOrder; }
}
