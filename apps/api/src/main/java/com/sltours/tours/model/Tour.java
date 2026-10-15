package com.sltours.tours.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tours")
public class Tour {
    @jakarta.persistence.Version
    private long version;

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 160)
    private String slug;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(nullable = false, length = 500)
    private String shortDescription;

    @Column(nullable = false, length = 2500)
    private String fullDescription;

    @Column(nullable = false, length = 80)
    private String duration;

    @Column(nullable = false)
    private int basePriceCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PricingType pricingType;

    @Column(nullable = false)
    private int minimumGuests;

    @Column(nullable = false)
    private int maximumGuests;

    @Column(nullable = false)
    private boolean pickupAvailable;

    @Column(nullable = false)
    private boolean featured;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TourStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TourCategory category;

    @Column(nullable = false, length = 220)
    private String heroImage;

    @Column(nullable = false, length = 220)
    private String seoTitle;

    @Column(nullable = false, length = 500)
    private String seoDescription;

    @Column(length = 160)
    private String cardNote;

    @Column(nullable = false)
    private int displayOrder = 100;

    @Column(length = 220)
    private String bannerImage;

    @Column(length = 160) private String pickupSummary;
    @Column(length = 80) private String groupSizeLabel;
    @Column(length = 160) private String availabilityLabel;
    @Column(length = 80) private String tourTypeLabel;
    @Column(length = 200) private String heroImageAlt;
    @Column(length = 600) private String itineraryNote;
    /** Admin only: open questions for the operator. Never shown publicly. */
    @Column(length = 4000) private String reviewNotes;
    @Column(length = 300) private String sourceUrl;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private EnquiryForm enquiryForm = EnquiryForm.STANDARD;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "tour_practical_info", joinColumns = @JoinColumn(name = "tour_id"))
    @OrderColumn(name = "item_order")
    @Column(name = "value", nullable = false, length = 600)
    private List<String> practicalInfo = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "tour_locations", joinColumns = @JoinColumn(name = "tour_id"))
    @OrderColumn(name = "item_order")
    @Column(name = "value", nullable = false)
    private List<String> locations = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "tour_tags", joinColumns = @JoinColumn(name = "tour_id"))
    @OrderColumn(name = "item_order")
    @Column(name = "value", nullable = false)
    private List<String> tags = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "tour_highlights", joinColumns = @JoinColumn(name = "tour_id"))
    @OrderColumn(name = "item_order")
    @Column(name = "value", nullable = false)
    private List<String> highlights = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "tour_included", joinColumns = @JoinColumn(name = "tour_id"))
    @OrderColumn(name = "item_order")
    @Column(name = "value", nullable = false)
    private List<String> included = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "tour_excluded", joinColumns = @JoinColumn(name = "tour_id"))
    @OrderColumn(name = "item_order")
    @Column(name = "value", nullable = false)
    private List<String> excluded = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "tour_itinerary", joinColumns = @JoinColumn(name = "tour_id"))
    @OrderColumn(name = "item_order")
    @Column(name = "value", nullable = false, length = 500)
    private List<String> itinerary = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "tour_faqs", joinColumns = @JoinColumn(name = "tour_id"))
    @OrderColumn(name = "item_order")
    @Column(name = "value", nullable = false, length = 800)
    private List<String> faqs = new ArrayList<>();

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public long getVersion() { return version; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int value) { displayOrder = value; }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public void setShortDescription(String shortDescription) {
        this.shortDescription = shortDescription;
    }

    public String getFullDescription() {
        return fullDescription;
    }

    public void setFullDescription(String fullDescription) {
        this.fullDescription = fullDescription;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public int getBasePriceCents() {
        return basePriceCents;
    }

    public void setBasePriceCents(int basePriceCents) {
        this.basePriceCents = basePriceCents;
    }

    public PricingType getPricingType() {
        return pricingType;
    }

    public void setPricingType(PricingType pricingType) {
        this.pricingType = pricingType;
    }

    public int getMinimumGuests() {
        return minimumGuests;
    }

    public void setMinimumGuests(int minimumGuests) {
        this.minimumGuests = minimumGuests;
    }

    public int getMaximumGuests() {
        return maximumGuests;
    }

    public void setMaximumGuests(int maximumGuests) {
        this.maximumGuests = maximumGuests;
    }

    public boolean isPickupAvailable() {
        return pickupAvailable;
    }

    public void setPickupAvailable(boolean pickupAvailable) {
        this.pickupAvailable = pickupAvailable;
    }

    public boolean isFeatured() {
        return featured;
    }

    public void setFeatured(boolean featured) {
        this.featured = featured;
    }

    public TourStatus getStatus() {
        return status;
    }

    public void setStatus(TourStatus status) {
        this.status = status;
    }

    public TourCategory getCategory() {
        return category;
    }

    public void setCategory(TourCategory category) {
        this.category = category;
    }

    public String getHeroImage() {
        return heroImage;
    }

    public void setHeroImage(String heroImage) {
        this.heroImage = heroImage;
    }

    public String getSeoTitle() {
        return seoTitle;
    }

    public void setSeoTitle(String seoTitle) {
        this.seoTitle = seoTitle;
    }

    public String getSeoDescription() {
        return seoDescription;
    }

    public void setSeoDescription(String seoDescription) {
        this.seoDescription = seoDescription;
    }

    public List<String> getLocations() {
        return locations;
    }

    public void setLocations(List<String> locations) {
        this.locations = locations;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public List<String> getHighlights() {
        return highlights;
    }

    public void setHighlights(List<String> highlights) {
        this.highlights = highlights;
    }

    public List<String> getIncluded() {
        return included;
    }

    public void setIncluded(List<String> included) {
        this.included = included;
    }

    public List<String> getExcluded() {
        return excluded;
    }

    public void setExcluded(List<String> excluded) {
        this.excluded = excluded;
    }

    public List<String> getItinerary() {
        return itinerary;
    }

    public void setItinerary(List<String> itinerary) {
        this.itinerary = itinerary;
    }

    public List<String> getFaqs() {
        return faqs;
    }

    public void setFaqs(List<String> faqs) {
        this.faqs = faqs;
    }

    public String getCardNote() {
        return cardNote;
    }

    public void setCardNote(String cardNote) {
        this.cardNote = cardNote;
    }

    public String getBannerImage() {
        return bannerImage;
    }

    public void setBannerImage(String bannerImage) {
        this.bannerImage = bannerImage;
    }

    public java.time.OffsetDateTime getUpdatedAt() { return updatedAt; }
    public String getPickupSummary() { return pickupSummary; }
    public void setPickupSummary(String v) { pickupSummary = v; }
    public String getGroupSizeLabel() { return groupSizeLabel; }
    public void setGroupSizeLabel(String v) { groupSizeLabel = v; }
    public String getAvailabilityLabel() { return availabilityLabel; }
    public void setAvailabilityLabel(String v) { availabilityLabel = v; }
    public String getTourTypeLabel() { return tourTypeLabel; }
    public void setTourTypeLabel(String v) { tourTypeLabel = v; }
    public String getHeroImageAlt() { return heroImageAlt; }
    public void setHeroImageAlt(String v) { heroImageAlt = v; }
    public String getItineraryNote() { return itineraryNote; }
    public void setItineraryNote(String v) { itineraryNote = v; }
    public String getReviewNotes() { return reviewNotes; }
    public void setReviewNotes(String v) { reviewNotes = v; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String v) { sourceUrl = v; }
    public EnquiryForm getEnquiryForm() { return enquiryForm; }
    public void setEnquiryForm(EnquiryForm v) { enquiryForm = v; }
    public List<String> getPracticalInfo() { return practicalInfo; }
    public void setPracticalInfo(List<String> v) { practicalInfo = v; }
}
