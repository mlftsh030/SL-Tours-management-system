package com.sltours.tripplanner.repository;

import com.sltours.tripplanner.model.TripPlanningRequest;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripPlanningRequestRepository extends JpaRepository<TripPlanningRequest, UUID> {
    boolean existsByReference(String reference);
}
