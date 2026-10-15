package com.sltours.enquiries.model;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Internal admin note. Notes are append-only so the handling history stays intact. */
@Entity
@Table(name = "enquiry_notes")
public class EnquiryNote {
    @Id private UUID id = UUID.randomUUID();
    @Column(nullable = false) private UUID enquiryId;
    @Column(nullable = false, length = 180) private String author;
    @Column(nullable = false, length = 2000) private String body;
    @Column(nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
    protected EnquiryNote() {}
    public EnquiryNote(UUID enquiryId, String author, String body) { this.enquiryId = enquiryId; this.author = author; this.body = body; }
    public UUID getId() { return id; }
    public String getAuthor() { return author; }
    public String getBody() { return body; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
