package com.sltours.enquiries.repository;
import com.sltours.enquiries.model.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface EnquiryRepository extends JpaRepository<Enquiry, UUID>, JpaSpecificationExecutor<Enquiry> {
    @EntityGraph(attributePaths = "tour") Optional<Enquiry> findByReference(String reference);
    Optional<Enquiry> findByIdempotencyKey(UUID idempotencyKey);
    Optional<Enquiry> findByTripPlanningRequestId(UUID tripPlanningRequestId);
    boolean existsByReference(String reference);
    long countByStatus(EnquiryStatus status);
}
