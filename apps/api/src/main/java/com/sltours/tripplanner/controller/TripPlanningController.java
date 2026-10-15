package com.sltours.tripplanner.controller;

import com.sltours.tripplanner.dto.CreateTripPlanningRequest;
import com.sltours.tripplanner.dto.TripPlanningResponse;
import com.sltours.tripplanner.service.TripPlanningService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trip-planning-requests")
public class TripPlanningController {
    private final TripPlanningService tripPlanningService;

    public TripPlanningController(TripPlanningService tripPlanningService) {
        this.tripPlanningService = tripPlanningService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TripPlanningResponse createRequest(@Valid @RequestBody CreateTripPlanningRequest request) {
        return tripPlanningService.createRequest(request);
    }
}
