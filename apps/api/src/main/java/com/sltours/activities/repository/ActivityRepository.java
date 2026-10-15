package com.sltours.activities.repository;
import com.sltours.activities.model.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;
public interface ActivityRepository extends JpaRepository<Activity, UUID> {
    Optional<Activity> findBySlug(String slug);
    Optional<Activity> findBySlugAndStatus(String slug, ActivityEnums.Status status);
    List<Activity> findByStatusOrderByTitleAsc(ActivityEnums.Status status);
    List<Activity> findAllByOrderByStatusAscTitleAsc();
    boolean existsBySlugAndIdNot(String slug, UUID id);
    boolean existsBySlug(String slug);
}
