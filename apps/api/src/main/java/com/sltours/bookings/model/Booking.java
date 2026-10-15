package com.sltours.bookings.model;

import com.sltours.tours.model.Tour;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "bookings")
public class Booking {
    @jakarta.persistence.Version
    private long version;
    private UUID accountId;

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 32)
    private String reference;

    /** Set for tour bookings; private trips have no tour. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tour_id")
    private Tour tour;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingType bookingType = BookingType.TOUR;

    private UUID idempotencyKey;
    /** The accepted quotation this booking was created from (QUOTED bookings). */
    private UUID sourceQuotationId;
    /** Title of a QUOTED booking; tour and private-trip bookings take theirs from the tour or trip. */
    @Column(length = 180) private String title;

    /** Development sample row from migration V2 (flagged by V12). */
    @Column(nullable = false)
    private boolean demoData;

    @Column(nullable = false)
    private LocalDate travelDate;

    @Column(nullable = false)
    private int guests;

    @Column(nullable = false, length = 160)
    private String customerName;

    @Column(nullable = false, length = 180)
    private String customerEmail;

    @Column(length = 80)
    private String customerPhone;

    @Column(length = 80)
    private String customerCountry;

    @Column(length = 240)
    private String pickupLocation;

    @Column(length = 1200)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private BookingStatus status;

    @Column(nullable = false)
    private int totalAmountCents;

    @Column(nullable = false)
    private int amountPaidCents;

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

    public boolean isDemoData() { return demoData; }
    public BookingType getBookingType() { return bookingType; }
    public void setBookingType(BookingType value) { bookingType = value; }
    public UUID getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(UUID value) { idempotencyKey = value; }
    /** Customer-facing title: the tour name, or "Private trip" for hourly bookings. */
    public String getTitle() { return title != null ? title : tour != null ? tour.getTitle() : "Private trip"; }
    public void setTitle(String value) { title = value; }
    public UUID getSourceQuotationId() { return sourceQuotationId; }
    public void setSourceQuotationId(UUID value) { sourceQuotationId = value; }
    public UUID getAccountId() { return accountId; }
    public void setAccountId(UUID value) { accountId = value; }
    public long getVersion() { return version; }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public Tour getTour() {
        return tour;
    }

    public void setTour(Tour tour) {
        this.tour = tour;
    }

    public LocalDate getTravelDate() {
        return travelDate;
    }

    public void setTravelDate(LocalDate travelDate) {
        this.travelDate = travelDate;
    }

    public int getGuests() {
        return guests;
    }

    public void setGuests(int guests) {
        this.guests = guests;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public String getCustomerCountry() {
        return customerCountry;
    }

    public void setCustomerCountry(String customerCountry) {
        this.customerCountry = customerCountry;
    }

    public String getPickupLocation() {
        return pickupLocation;
    }

    public void setPickupLocation(String pickupLocation) {
        this.pickupLocation = pickupLocation;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public int getTotalAmountCents() {
        return totalAmountCents;
    }

    public void setTotalAmountCents(int totalAmountCents) {
        this.totalAmountCents = totalAmountCents;
    }

    public int getAmountPaidCents() {
        return amountPaidCents;
    }

    public void setAmountPaidCents(int amountPaidCents) {
        this.amountPaidCents = amountPaidCents;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
