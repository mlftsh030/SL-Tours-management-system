package com.sltours.inspections.repository;
import com.sltours.inspections.model.Inspection;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface InspectionRepository extends JpaRepository<Inspection, UUID> {
    /** The current (not rejected) inspection of a type for an assignment; at most one exists. */
    default Optional<Inspection> findLive(UUID assignmentId, Inspection.Type type) {
        return findExcludingStatus(assignmentId, type, Inspection.ReviewStatus.REJECTED);
    }
    @Query("select i from Inspection i where i.assignmentId = :assignmentId and i.type = :type and i.reviewStatus <> :excluded")
    Optional<Inspection> findExcludingStatus(@Param("assignmentId") UUID assignmentId, @Param("type") Inspection.Type type,
        @Param("excluded") Inspection.ReviewStatus excluded);
    List<Inspection> findByAssignmentIdOrderBySubmittedAtAsc(UUID assignmentId);
    List<Inspection> findByReviewStatusOrderBySubmittedAtAsc(Inspection.ReviewStatus status);
    long countByReviewStatus(Inspection.ReviewStatus status);
    Optional<Inspection> findBySelfieMediaId(UUID mediaId);
}
