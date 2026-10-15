package com.sltours.activities.model;
import com.sltours.activities.model.ActivityEnums.*;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

/** One published price with the conditions under which it applies. */
@Entity
@Table(name = "activity_price_rules")
public class ActivityPriceRule {
    @Id private UUID id = UUID.randomUUID();
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "activity_id") private Activity activity;
    @Column(nullable = false, length = 160) private String label;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Audience audience;
    private Integer minAge;
    private Integer maxAge;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Residency residency = Residency.ANY;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Unit unit;
    @Column(nullable = false) private int amountCents;
    @Column(length = 40) private String daysOfWeek;
    private LocalTime timeFrom;
    private LocalTime timeTo;
    @Column(length = 400) private String conditions;
    @Column(nullable = false) private boolean estimateDefault;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private UUID sourceId;
    @Column(nullable = false) private LocalDate verifiedOn;
    @Column(nullable = false) private int sortOrder;
    @Column(length = 400) private String uncertaintyNote;
    @Column(nullable = false) private boolean promotional;
    protected ActivityPriceRule() {}
    public ActivityPriceRule(Activity activity) { this.activity = activity; }
    public UUID getId() { return id; }
    public String getLabel() { return label; }
    public Audience getAudience() { return audience; }
    public Integer getMinAge() { return minAge; }
    public Integer getMaxAge() { return maxAge; }
    public Residency getResidency() { return residency; }
    public Unit getUnit() { return unit; }
    public int getAmountCents() { return amountCents; }
    public String getDaysOfWeek() { return daysOfWeek; }
    public LocalTime getTimeFrom() { return timeFrom; }
    public LocalTime getTimeTo() { return timeTo; }
    public String getConditions() { return conditions; }
    public boolean isEstimateDefault() { return estimateDefault; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public UUID getSourceId() { return sourceId; }
    public LocalDate getVerifiedOn() { return verifiedOn; }
    public int getSortOrder() { return sortOrder; }
    /** Set when official sources conflict or leave eligibility undefined; the price is then only an estimate. */
    public String getUncertaintyNote() { return uncertaintyNote; }
    public boolean isPromotional() { return promotional; }
    public void setQualifiers(String uncertaintyNote, boolean promotional) { this.uncertaintyNote = uncertaintyNote; this.promotional = promotional; }
    public void set(String label, Audience audience, Integer minAge, Integer maxAge, Residency residency, Unit unit, int amountCents,
            String daysOfWeek, LocalTime timeFrom, LocalTime timeTo, String conditions, boolean estimateDefault, LocalDate effectiveFrom,
            LocalDate effectiveTo, UUID sourceId, LocalDate verifiedOn, int sortOrder) {
        this.label = label; this.audience = audience; this.minAge = minAge; this.maxAge = maxAge; this.residency = residency; this.unit = unit;
        this.amountCents = amountCents; this.daysOfWeek = daysOfWeek; this.timeFrom = timeFrom; this.timeTo = timeTo; this.conditions = conditions;
        this.estimateDefault = estimateDefault; this.effectiveFrom = effectiveFrom; this.effectiveTo = effectiveTo; this.sourceId = sourceId;
        this.verifiedOn = verifiedOn; this.sortOrder = sortOrder;
    }
    public boolean effectiveOn(LocalDate date) {
        return (effectiveFrom == null || !date.isBefore(effectiveFrom)) && (effectiveTo == null || !date.isAfter(effectiveTo));
    }
}
