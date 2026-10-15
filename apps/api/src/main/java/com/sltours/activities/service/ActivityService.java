package com.sltours.activities.service;
import com.sltours.activities.dto.ActivityDtos.*;
import com.sltours.activities.model.*;
import com.sltours.activities.model.ActivityEnums.*;
import com.sltours.activities.repository.ActivityRepository;
import com.sltours.shared.error.ResourceNotFoundException;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public catalogue: published activities only. */
@Service
public class ActivityService {
    private final ActivityRepository activities;
    private final ActivityViews views;
    public ActivityService(ActivityRepository activities, ActivityViews views) { this.activities = activities; this.views = views; }

    @Transactional(readOnly = true)
    public List<ActivityCard> list(Category category, Area area, String query) { return list(category, area, null, null, query); }

    @Transactional(readOnly = true)
    public List<ActivityCard> list(Category category, Area area, Region region, Theme theme, String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return activities.findByStatusOrderByTitleAsc(Status.PUBLISHED).stream()
            .filter(a -> category == null || a.getCategory() == category)
            .filter(a -> area == null || a.getArea() == area)
            .filter(a -> region == null || a.getRegion() == region)
            .filter(a -> theme == null || a.getThemes().contains(theme))
            .filter(a -> q.isEmpty() || (a.getTitle() + " " + a.getSummary() + " " + Objects.toString(a.getTown(), "")).toLowerCase(Locale.ROOT).contains(q))
            .map(views::card).toList();
    }

    /** Prices of a published activity, for the day planner and planning assistant only. */
    @Transactional(readOnly = true)
    public ActivityPricing pricing(String slug) {
        return views.pricing(activities.findBySlugAndStatus(slug, Status.PUBLISHED).orElseThrow(() -> new ResourceNotFoundException("Activity not found")),
            ActivityViews.today());
    }

    @Transactional(readOnly = true)
    public ActivityPage get(String slug) {
        return views.page(activities.findBySlugAndStatus(slug, Status.PUBLISHED).orElseThrow(() -> new ResourceNotFoundException("Activity not found")));
    }

    @Transactional(readOnly = true)
    public List<Activity> published() {
        List<Activity> list = activities.findByStatusOrderByTitleAsc(Status.PUBLISHED);
        // Initialise collections for use outside the transaction.
        list.forEach(a -> { a.getPriceRules().size(); a.getHours().size(); a.getExtras().size(); a.getSources().size(); a.getFaqs().size();
            a.getMedia().size(); a.getThemes().size(); });
        return list;
    }
}
