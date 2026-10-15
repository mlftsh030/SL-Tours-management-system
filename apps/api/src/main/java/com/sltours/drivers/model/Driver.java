package com.sltours.drivers.model;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "drivers")
public class Driver {
    public enum Status { ACTIVE, INACTIVE }
    public enum Verification { PENDING, VERIFIED, REJECTED, SUSPENDED }
    @Id private UUID id = UUID.randomUUID();
    @Version private Long version;
    @Column(nullable = false, length = 160) private String name;
    @Column(length = 120) private String contact;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private Status status = Status.ACTIVE;
    private String notes;
    private UUID accountId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Verification verificationStatus = Verification.PENDING;
    @Column(length = 40) private String licenceNumber;
    private LocalDate licenceExpiresOn;
    private LocalDate prdpExpiresOn;
    @Column(length = 1000) private String verificationNotes;
    @Column(length = 180) private String verifiedBy;
    private OffsetDateTime verifiedAt;
    @Column(nullable = false) private OffsetDateTime createdAt;
    /** Development sample row from migration V2 (flagged by V12); excluded from inventory outside dev/test. */
    @Column(nullable = false) private boolean demoData;
    @Column(nullable = false) private OffsetDateTime updatedAt;
    @PrePersist void onCreate() { createdAt = OffsetDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void onUpdate() { updatedAt = OffsetDateTime.now(); }
    public boolean isDemoData() { return demoData; }
    public void setDemoData(boolean value) { demoData = value; }
    public UUID getId() { return id; }
    public long getVersion() { return version == null ? 0 : version; }
    public String getName() { return name; }
    public void setName(String value) { name = value; }
    public String getContact() { return contact; }
    public void setContact(String value) { contact = value; }
    public Status getStatus() { return status; }
    public void setStatus(Status value) { status = value; }
    public String getNotes() { return notes; }
    public void setNotes(String value) { notes = value; }
    public UUID getAccountId() { return accountId; }
    public void setAccountId(UUID value) { accountId = value; }
    public Verification getVerificationStatus() { return verificationStatus; }
    public String getLicenceNumber() { return licenceNumber; }
    public void setLicenceNumber(String value) { licenceNumber = value; }
    public LocalDate getLicenceExpiresOn() { return licenceExpiresOn; }
    public void setLicenceExpiresOn(LocalDate value) { licenceExpiresOn = value; }
    public LocalDate getPrdpExpiresOn() { return prdpExpiresOn; }
    public void setPrdpExpiresOn(LocalDate value) { prdpExpiresOn = value; }
    public String getVerificationNotes() { return verificationNotes; }
    public String getVerifiedBy() { return verifiedBy; }
    public OffsetDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerification(Verification status, String notes, String actor) {
        verificationStatus = status; verificationNotes = notes; verifiedBy = actor; verifiedAt = OffsetDateTime.now();
    }
}
