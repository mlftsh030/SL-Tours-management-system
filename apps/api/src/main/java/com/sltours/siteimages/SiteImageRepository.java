package com.sltours.siteimages;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SiteImageRepository extends JpaRepository<SiteImage, UUID> {
    List<SiteImage> findBySlotOrderBySortOrderAscCreatedAtAsc(SiteImage.Slot slot);
    List<SiteImage> findBySlotAndActiveTrueOrderBySortOrderAscCreatedAtAsc(SiteImage.Slot slot);
}
