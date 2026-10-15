package com.sltours.activities.controller;
import com.sltours.activities.dto.ActivityDtos.*;
import com.sltours.activities.dto.ItineraryDtos.*;
import com.sltours.activities.model.ActivityEnums.*;
import com.sltours.activities.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @Validated
@RequestMapping("/api/v1")
public class ActivityController {
    private final ActivityService activities;
    private final ItineraryService itineraries;
    public ActivityController(ActivityService activities, ItineraryService itineraries) { this.activities = activities; this.itineraries = itineraries; }
    @GetMapping("/activities")
    List<ActivityCard> list(@RequestParam(required = false) Category category, @RequestParam(required = false) Area area,
            @RequestParam(required = false) Region region, @RequestParam(required = false) Theme theme,
            @RequestParam(required = false) @Size(max = 80) String q) { return activities.list(category, area, region, theme, q); }
    @GetMapping("/activities/{slug}") ActivityPage get(@PathVariable String slug) { return activities.get(slug); }
    /** Calculates a plan; nothing is stored. */
    @PostMapping("/itineraries") Itinerary plan(@Valid @RequestBody ItineraryRequest request) { return itineraries.plan(request); }
}
