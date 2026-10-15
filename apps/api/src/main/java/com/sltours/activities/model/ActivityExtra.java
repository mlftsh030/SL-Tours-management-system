package com.sltours.activities.model;
import com.sltours.activities.model.ActivityEnums.Unit;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

/** A mandatory or optional additional cost, such as parking or equipment hire. */
@Entity
@Table(name = "activity_extras")
public class ActivityExtra {
    @Id private UUID id = UUID.randomUUID();
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "activity_id") private Activity activity;
    @Column(nullable = false, length = 160) private String label;
    @Column(nullable = false) private int amountCents;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Unit unit;
    @Column(nullable = false) private boolean mandatory;
    @Column(length = 400) private String conditions;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private UUID sourceId;
    @Column(nullable = false) private LocalDate verifiedOn;
    @Column(nullable = false) private int sortOrder;
    @Column(length = 400) private String uncertaintyNote;
    protected ActivityExtra() {}
    public ActivityExtra(Activity activity) { this.activity = activity; }
    public UUID getId() { return id; }
    public String getLabel() { return label; }
    public int getAmountCents() { return amountCents; }
    public Unit getUnit() { return unit; }
    public boolean isMandatory() { return mandatory; }
    public String getConditions() { return conditions; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public UUID getSourceId() { return sourceId; }
    public LocalDate getVerifiedOn() { return verifiedOn; }
    public String getUncertaintyNote() { return uncertaintyNote; }
    public void setUncertaintyNote(String v) { uncertaintyNote = v; }
    public void set(String label, int amountCents, Unit unit, boolean mandatory, String conditions, LocalDate effectiveFrom, LocalDate effectiveTo,
            UUID sourceId, LocalDate verifiedOn, int sortOrder) {
        this.label = label; this.amountCents = amountCents; this.unit = unit; this.mandatory = mandatory; this.conditions = conditions;
        this.effectiveFrom = effectiveFrom; this.effectiveTo = effectiveTo; this.sourceId = sourceId; this.verifiedOn = verifiedOn; this.sortOrder = sortOrder;
    }
    public boolean effectiveOn(LocalDate date) {
        return (effectiveFrom == null || !date.isBefore(effectiveFrom)) && (effectiveTo == null || !date.isAfter(effectiveTo));
    }
}
