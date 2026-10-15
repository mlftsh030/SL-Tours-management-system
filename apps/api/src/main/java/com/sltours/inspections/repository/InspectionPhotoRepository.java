package com.sltours.inspections.repository;
import com.sltours.inspections.model.InspectionPhoto;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface InspectionPhotoRepository extends JpaRepository<InspectionPhoto, UUID> {
    List<InspectionPhoto> findByInspectionIdOrderBySortOrderAsc(UUID inspectionId);
    Optional<InspectionPhoto> findByMediaId(UUID mediaId);
}
