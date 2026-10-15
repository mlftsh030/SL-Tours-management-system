package com.sltours.activities.model;
import com.sltours.activities.model.ActivityEnums.*;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "activities")
public class Activity {
    @Id private UUID id = UUID.randomUUID();
    @Version private Long version;
    @Column(nullable = false, unique = true, length = 160) private String slug;
    @Column(nullable = false, length = 180) private String title;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private Category category;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status = Status.DRAFT;
    @Column(nullable = false, length = 400) private String summary;
    @Column(nullable = false, length = 4000) private String description;
    /** Cape Town planning area; null outside Cape Town's planning areas. */
    @Enumerated(EnumType.STRING) @Column(length = 30) private Area area;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private Region region = Region.CAPE_TOWN;
    @Column(length = 120) private String town;
    @Column(precision = 8, scale = 5) private java.math.BigDecimal latitude;
    @Column(precision = 8, scale = 5) private java.math.BigDecimal longitude;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Kind kind = Kind.ATTRACTION;
    @Column(length = 400) private String eventSchedule;
    @Column(length = 2000) private String whatToExpect;
    @Column(length = 2000) private String practicalInfo;
    @Column(length = 1000) private String seasonalInfo;
    /** As published by the operator; null means accessibility information is not published. */
    @Column(length = 1000) private String accessibilityInfo;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private OperatingStatus operatingStatus = OperatingStatus.OPEN;
    @Column(length = 400) private String operatingNote;
    private LocalDate operatingCheckedOn;
    private LocalDate existenceVerifiedOn;
    @Column(length = 300) private String address;
    @Column(length = 500) private String officialUrl;
    @Column(name = "typical_duration_minutes", nullable = false) private int durationMinutes;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ScheduleType scheduleType;
    @Column(nullable = false) private boolean weatherDependent;
    @Column(nullable = false) private boolean indoor;
    private Integer minAge;
    @Column(length = 300) private String accompanimentRule;
    @Column(length = 600) private String bookingNotes;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private PriceStatus priceStatus;
    @Column(length = 2000) private String verificationNotes;
    private LocalDate lastVerifiedOn;
    @Column(length = 180) private String verifiedBy;
    @Column(length = 220) private String heroImage;
    @Column(length = 200) private String heroImageAlt;
    @Column(length = 180) private String seoTitle;
    @Column(length = 300) private String seoDescription;
    private OffsetDateTime publishedAt;
    /** Set when the entry came from an approved supplier proposal; written only by the partnership service. */
    @Column(insertable = false, updatable = false) private UUID supplierPartnerId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ParticipationRules participationRules = ParticipationRules.KNOWN;
    @Column(length = 400) private String participationNote;
    @Column(nullable = false) private OffsetDateTime createdAt;
    @Column(nullable = false) private OffsetDateTime updatedAt;
    @OneToMany(mappedBy = "activity", cascade = CascadeType.ALL, orphanRemoval = true) @OrderBy("sortOrder")
    private List<ActivitySource> sources = new ArrayList<>();
    @OneToMany(mappedBy = "activity", cascade = CascadeType.ALL, orphanRemoval = true) @OrderBy("sortOrder")
    private List<ActivityPriceRule> priceRules = new ArrayList<>();
    @OneToMany(mappedBy = "activity", cascade = CascadeType.ALL, orphanRemoval = true) @OrderBy("sortOrder")
    private List<ActivityExtra> extras = new ArrayList<>();
    @OneToMany(mappedBy = "activity", cascade = CascadeType.ALL, orphanRemoval = true) @OrderBy("sortOrder")
    private List<ActivityHours> hours = new ArrayList<>();
    @OneToMany(mappedBy = "activity", cascade = CascadeType.ALL, orphanRemoval = true) @OrderBy("sortOrder")
    private List<ActivityFaq> faqs = new ArrayList<>();
    @OneToMany(mappedBy = "activity", cascade = CascadeType.ALL, orphanRemoval = true) @OrderBy("sortOrder")
    private List<ActivityMedia> media = new ArrayList<>();
    @ElementCollection @CollectionTable(name = "activity_themes", joinColumns = @JoinColumn(name = "activity_id"))
    @Enumerated(EnumType.STRING) @Column(name = "theme", length = 30) private Set<Theme> themes = new TreeSet<>();
    @PrePersist void onCreate() { createdAt = OffsetDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public UUID getId() { return id; }
    public long getVersion() { return version == null ? 0 : version; }
    public String getSlug() { return slug; }
    public void setSlug(String v) { slug = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { title = v; }
    public Category getCategory() { return category; }
    public void setCategory(Category v) { category = v; }
    public Status getStatus() { return status; }
    public void setStatus(Status v) { status = v; }
    public String getSummary() { return summary; }
    public void setSummary(String v) { summary = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { description = v; }
    public Area getArea() { return area; }
    public void setArea(Area v) { area = v; }
    public String getAddress() { return address; }
    public void setAddress(String v) { address = v; }
    public String getOfficialUrl() { return officialUrl; }
    public void setOfficialUrl(String v) { officialUrl = v; }
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int v) { durationMinutes = v; }
    public ScheduleType getScheduleType() { return scheduleType; }
    public void setScheduleType(ScheduleType v) { scheduleType = v; }
    public boolean isWeatherDependent() { return weatherDependent; }
    public void setWeatherDependent(boolean v) { weatherDependent = v; }
    public boolean isIndoor() { return indoor; }
    public void setIndoor(boolean v) { indoor = v; }
    public Integer getMinAge() { return minAge; }
    public void setMinAge(Integer v) { minAge = v; }
    public String getAccompanimentRule() { return accompanimentRule; }
    public void setAccompanimentRule(String v) { accompanimentRule = v; }
    public String getBookingNotes() { return bookingNotes; }
    public void setBookingNotes(String v) { bookingNotes = v; }
    public PriceStatus getPriceStatus() { return priceStatus; }
    public void setPriceStatus(PriceStatus v) { priceStatus = v; }
    public String getVerificationNotes() { return verificationNotes; }
    public void setVerificationNotes(String v) { verificationNotes = v; }
    public LocalDate getLastVerifiedOn() { return lastVerifiedOn; }
    public String getVerifiedBy() { return verifiedBy; }
    public void markVerified(LocalDate on, String by) { lastVerifiedOn = on; verifiedBy = by; }
    public String getHeroImage() { return heroImage; }
    public void setHeroImage(String v) { heroImage = v; }
    public String getHeroImageAlt() { return heroImageAlt; }
    public void setHeroImageAlt(String v) { heroImageAlt = v; }
    public String getSeoTitle() { return seoTitle; }
    public void setSeoTitle(String v) { seoTitle = v; }
    public String getSeoDescription() { return seoDescription; }
    public void setSeoDescription(String v) { seoDescription = v; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(OffsetDateTime v) { publishedAt = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public UUID getSupplierPartnerId() { return supplierPartnerId; }
    public ParticipationRules getParticipationRules() { return participationRules; }
    public void setParticipationRules(ParticipationRules v) { participationRules = v; }
    public String getParticipationNote() { return participationNote; }
    public void setParticipationNote(String v) { participationNote = v; }
    public List<ActivitySource> getSources() { return sources; }
    public List<ActivityPriceRule> getPriceRules() { return priceRules; }
    public List<ActivityExtra> getExtras() { return extras; }
    public List<ActivityHours> getHours() { return hours; }
    public List<ActivityFaq> getFaqs() { return faqs; }
    public List<ActivityMedia> getMedia() { return media; }
    public Set<Theme> getThemes() { return themes; }
    public Region getRegion() { return region; }
    public void setRegion(Region v) { region = v; }
    public String getTown() { return town; }
    public void setTown(String v) { town = v; }
    public java.math.BigDecimal getLatitude() { return latitude; }
    public java.math.BigDecimal getLongitude() { return longitude; }
    public void setCoordinates(java.math.BigDecimal lat, java.math.BigDecimal lon) { latitude = lat; longitude = lon; }
    public Kind getKind() { return kind; }
    public void setKind(Kind v) { kind = v; }
    public String getEventSchedule() { return eventSchedule; }
    public void setEventSchedule(String v) { eventSchedule = v; }
    public String getWhatToExpect() { return whatToExpect; }
    public void setWhatToExpect(String v) { whatToExpect = v; }
    public String getPracticalInfo() { return practicalInfo; }
    public void setPracticalInfo(String v) { practicalInfo = v; }
    public String getSeasonalInfo() { return seasonalInfo; }
    public void setSeasonalInfo(String v) { seasonalInfo = v; }
    public String getAccessibilityInfo() { return accessibilityInfo; }
    public void setAccessibilityInfo(String v) { accessibilityInfo = v; }
    public OperatingStatus getOperatingStatus() { return operatingStatus; }
    public void setOperatingStatus(OperatingStatus v) { operatingStatus = v; }
    public String getOperatingNote() { return operatingNote; }
    public void setOperatingNote(String v) { operatingNote = v; }
    public LocalDate getOperatingCheckedOn() { return operatingCheckedOn; }
    public void setOperatingCheckedOn(LocalDate v) { operatingCheckedOn = v; }
    public LocalDate getExistenceVerifiedOn() { return existenceVerifiedOn; }
    public void setExistenceVerifiedOn(LocalDate v) { existenceVerifiedOn = v; }
    /** Public pictures in display order: the first is the card image. */
    public List<ActivityMedia> publicImages() {
        return media.stream().filter(m -> m.getKind() == MediaKind.IMAGE && m.isPublic()).toList();
    }
}
