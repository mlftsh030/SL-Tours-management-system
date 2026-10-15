package com.sltours.assignments.controller;
import com.sltours.assignments.dto.AssignmentDtos.*;
import com.sltours.assignments.service.AdminAssignmentService;
import com.sltours.inspections.dto.InspectionDtos.*;
import com.sltours.inspections.service.InspectionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** Staff may view assignments and inspections; assigning resources and reviewing inspections are administrator-only. */
@RestController @Validated
@RequestMapping("/api/v1/admin")
public class AdminAssignmentController {
    private final AdminAssignmentService assignments;
    private final InspectionService inspections;
    public AdminAssignmentController(AdminAssignmentService assignments, InspectionService inspections) {
        this.assignments = assignments; this.inspections = inspections;
    }
    @GetMapping("/assignments") List<AdminAssignment> list(@RequestParam(defaultValue = "active") @Pattern(regexp = "active|recent") String scope) {
        return assignments.list(scope);
    }
    /** Includes driver eligibility details, which are verification data and therefore administrator-only. */
    @GetMapping("/assignments/attention") List<AdminAssignment> attention() { return assignments.needingAttention(); }
    @GetMapping("/assignments/options") @PreAuthorize("hasRole('ADMIN')")
    ResourceOptions options(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startsAt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endsAt,
            @RequestParam(defaultValue = "1") @Min(1) @Max(80) int passengers) {
        return assignments.options(startsAt, endsAt, passengers);
    }
    @PostMapping("/assignments") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')")
    AdminAssignment create(@Valid @RequestBody CreateAssignmentRequest request) { return assignments.create(request); }
    @PutMapping("/assignments/{id}") @PreAuthorize("hasRole('ADMIN')")
    AdminAssignment change(@PathVariable UUID id, @Valid @RequestBody ChangeAssignmentRequest request) { return assignments.change(id, request); }
    @PostMapping("/assignments/{id}/cancel") @PreAuthorize("hasRole('ADMIN')")
    AdminAssignment cancel(@PathVariable UUID id, @Valid @RequestBody CancelAssignmentRequest request) { return assignments.cancel(id, request); }
    @GetMapping("/assignments/{id}/inspections") Comparison compare(@PathVariable UUID id) { return inspections.compare(id); }
    @GetMapping("/inspections/pending") List<PendingReview> pending() { return inspections.pendingReviews(); }
    @GetMapping("/inspections/{id}") InspectionView inspection(@PathVariable UUID id) { return inspections.get(id); }
    @PostMapping("/inspections/{id}/approve") @PreAuthorize("hasRole('ADMIN')")
    InspectionView approve(@PathVariable UUID id, @Valid @RequestBody ReviewRequest request) { return inspections.approve(id, request); }
    @PostMapping("/inspections/{id}/reject") @PreAuthorize("hasRole('ADMIN')")
    InspectionView reject(@PathVariable UUID id, @Valid @RequestBody ReviewRequest request) { return inspections.reject(id, request); }
}
