package com.sltours.activities.controller;
import com.sltours.activities.dto.ActivityDtos.*;
import com.sltours.activities.model.ActivityEnums.Status;
import com.sltours.activities.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Catalogue editing and planning assumptions are administrator-only. */
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/v1/admin")
public class AdminActivityController {
    public record VersionRequest(@NotNull @Min(0) Long version) {}
    public record StatusRequest(@NotNull @Min(0) Long version, @NotNull Status status) {}
    public record PlanningRequest(@NotNull @Min(0) Long version, @NotNull @DecimalMin("1.0") @DecimalMax("3.0") BigDecimal roadFactor,
        @Min(10) @Max(120) int averageSpeedKmh, @Min(0) @Max(60) int bufferMinutes, @Min(0) @Max(180) int lunchMinutes) {}
    private final ActivityAdminService activities;
    private final PlanningService planning;
    public AdminActivityController(ActivityAdminService activities, PlanningService planning) { this.activities = activities; this.planning = planning; }
    @GetMapping("/activities") List<AdminActivitySummary> list() { return activities.list(); }
    @GetMapping("/activities/{id}") AdminActivity get(@PathVariable UUID id) { return activities.get(id); }
    @PostMapping("/activities") @ResponseStatus(HttpStatus.CREATED) AdminActivity create(@Valid @RequestBody SaveActivityRequest request) { return activities.create(request); }
    @PutMapping("/activities/{id}") AdminActivity update(@PathVariable UUID id, @Valid @RequestBody SaveActivityRequest request) { return activities.update(id, request); }
    @PostMapping("/activities/{id}/verify") AdminActivity verify(@PathVariable UUID id, @Valid @RequestBody VersionRequest request) { return activities.verify(id, request.version()); }
    @PostMapping("/activities/{id}/status") AdminActivity status(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
        return activities.setStatus(id, request.version(), request.status());
    }
    @GetMapping("/planning") PlanningService.Settings planning() { return planning.settings(); }
    @PutMapping("/planning") PlanningService.Settings savePlanning(@Valid @RequestBody PlanningRequest r) {
        return planning.update(r.version(), r.roadFactor(), r.averageSpeedKmh(), r.bufferMinutes(), r.lunchMinutes());
    }
}
