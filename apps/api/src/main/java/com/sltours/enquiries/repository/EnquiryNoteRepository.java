package com.sltours.enquiries.repository;
import com.sltours.enquiries.model.EnquiryNote;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EnquiryNoteRepository extends JpaRepository<EnquiryNote, UUID> {
    List<EnquiryNote> findByEnquiryIdOrderByCreatedAtAsc(UUID enquiryId);
}
