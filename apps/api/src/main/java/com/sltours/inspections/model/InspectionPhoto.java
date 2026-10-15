package com.sltours.inspections.model;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "inspection_photos")
public class InspectionPhoto {
    public enum Kind { VEHICLE, DEFECT }
    @Id private UUID id = UUID.randomUUID();
    @Column(nullable = false) private UUID inspectionId;
    @Column(nullable = false, unique = true) private UUID mediaId;
    @Enumerated(EnumType.STRING) @Column(name = "photo_kind", nullable = false, length = 20) private Kind kind;
    @Enumerated(EnumType.STRING) @Column(length = 40) private ChecklistItem checklistItem;
    @Column(nullable = false) private int sortOrder;
    protected InspectionPhoto() {}
    public InspectionPhoto(UUID inspectionId, UUID mediaId, Kind kind, ChecklistItem checklistItem, int sortOrder) {
        this.inspectionId = inspectionId; this.mediaId = mediaId; this.kind = kind; this.checklistItem = checklistItem; this.sortOrder = sortOrder;
    }
    public UUID getInspectionId() { return inspectionId; }
    public UUID getMediaId() { return mediaId; }
    public Kind getKind() { return kind; }
    public ChecklistItem getChecklistItem() { return checklistItem; }
    public int getSortOrder() { return sortOrder; }
}
