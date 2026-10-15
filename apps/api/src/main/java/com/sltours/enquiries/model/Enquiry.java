package com.sltours.enquiries.model;
import com.sltours.tours.model.Tour;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "enquiries")
public class Enquiry {
    @Id private UUID id = UUID.randomUUID();
    @Version private Long version;
    @Column(nullable = false, unique = true, length = 32) private String reference;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private EnquirySource source;
    @Enumerated(EnumType.STRING) @Column(name = "enquiry_type", nullable = false, length = 20) private EnquiryType type;
    @Column(nullable = false, length = 160) private String customerName;
    @Column(nullable = false, length = 180) private String customerEmail;
    @Column(length = 80) private String customerPhone;
    @Column(nullable = false, length = 4000) private String message;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "tour_id") private Tour tour;
    private LocalDate requestedDate;
    private LocalTime requestedTime;
    private Integer durationMinutes;
    @Column(length = 240) private String pickupLocation;
    @Column(length = 1200) private String destination;
    private Integer passengers;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private EnquiryStatus status = EnquiryStatus.NEW;
    private UUID tripPlanningRequestId;
    private UUID convertedBookingId;
    private UUID accountId;
    private UUID idempotencyKey;
    @Column(nullable = false) private OffsetDateTime createdAt;
    /** Development sample row from migration V2 (flagged by V12); excluded from inventory outside dev/test. */
    @Column(nullable = false) private boolean demoData;
    @Column(nullable = false) private OffsetDateTime updatedAt;
    @PrePersist void onCreate() { createdAt = OffsetDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public UUID getAccountId() { return accountId; }
    public void setAccountId(UUID value) { accountId = value; }
    public boolean isDemoData() { return demoData; }
    public void setDemoData(boolean value) { demoData = value; }
    public UUID getId() { return id; }
    public long getVersion() { return version == null ? 0 : version; }
    public String getReference() { return reference; }
    public void setReference(String value) { reference = value; }
    public EnquirySource getSource() { return source; }
    public void setSource(EnquirySource value) { source = value; }
    public EnquiryType getType() { return type; }
    public void setType(EnquiryType value) { type = value; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String value) { customerName = value; }
    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String value) { customerEmail = value; }
    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String value) { customerPhone = value; }
    public String getMessage() { return message; }
    public void setMessage(String value) { message = value; }
    public Tour getTour() { return tour; }
    public void setTour(Tour value) { tour = value; }
    public LocalDate getRequestedDate() { return requestedDate; }
    public void setRequestedDate(LocalDate value) { requestedDate = value; }
    public LocalTime getRequestedTime() { return requestedTime; }
    public void setRequestedTime(LocalTime value) { requestedTime = value; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer value) { durationMinutes = value; }
    public String getPickupLocation() { return pickupLocation; }
    public void setPickupLocation(String value) { pickupLocation = value; }
    public String getDestination() { return destination; }
    public void setDestination(String value) { destination = value; }
    public Integer getPassengers() { return passengers; }
    public void setPassengers(Integer value) { passengers = value; }
    public EnquiryStatus getStatus() { return status; }
    public void setStatus(EnquiryStatus value) { status = value; }
    public UUID getTripPlanningRequestId() { return tripPlanningRequestId; }
    public void setTripPlanningRequestId(UUID value) { tripPlanningRequestId = value; }
    public UUID getConvertedBookingId() { return convertedBookingId; }
    public void setConvertedBookingId(UUID value) { convertedBookingId = value; }
    public UUID getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(UUID value) { idempotencyKey = value; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
