package com.sltours.assistant.model;
import com.sltours.activities.dto.ItineraryDtos.Itinerary;
import com.sltours.activities.model.ActivityEnums.Category;
import java.util.*;

/** One planner result offered to the visitor, with the inputs needed to recalculate it after a swap or a change of details. */
public record PlanOption(String id, String label, List<Category> interests, List<String> mustInclude, List<String> exclude, int stops,
    boolean onlyPublishedPrices, Itinerary itinerary) {}
