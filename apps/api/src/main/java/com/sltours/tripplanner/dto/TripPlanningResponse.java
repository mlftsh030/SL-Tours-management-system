package com.sltours.tripplanner.dto;

import com.sltours.tripplanner.model.TripPlanningStatus;

public record TripPlanningResponse(
        String reference,
        TripPlanningStatus status,
        String message
) {
}
