package com.sltours.tours.repository;

import com.sltours.tours.model.Tour;
import com.sltours.tours.model.TourCategory;
import com.sltours.tours.model.TourStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TourRepository extends JpaRepository<Tour, UUID> {
    boolean existsBySlug(String slug);
    boolean existsBySlugAndIdNot(String slug, UUID id);

    Optional<Tour> findBySlugAndStatus(String slug, TourStatus status);
    Optional<Tour> findBySlug(String slug);
    List<Tour> findByStatusOrderByDisplayOrderAscTitleAsc(TourStatus status);

    List<Tour> findByStatusOrderByFeaturedDescDisplayOrderAscTitleAsc(TourStatus status);

    List<Tour> findByCategoryAndStatusOrderByDisplayOrderAscTitleAsc(TourCategory category, TourStatus status);

    List<Tour> findByFeaturedTrueAndStatusOrderByDisplayOrderAscTitleAsc(TourStatus status);
}
