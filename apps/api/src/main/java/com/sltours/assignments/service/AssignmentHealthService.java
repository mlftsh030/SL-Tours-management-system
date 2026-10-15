package com.sltours.assignments.service;
import com.sltours.accounts.model.Account;
import com.sltours.accounts.repository.AccountRepository;
import com.sltours.assignments.model.*;
import com.sltours.drivers.model.Driver;
import com.sltours.drivers.repository.DriverRepository;
import com.sltours.drivers.service.DriverEligibility;
import com.sltours.fleet.model.*;
import com.sltours.fleet.repository.*;
import com.sltours.shared.demo.DemoDataPolicy;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finds upcoming assignments that can no longer go ahead as planned: the vehicle is restricted, out of service or a
 * sample record, or the driver is no longer eligible on the trip date. Used for admin warnings and reassignment.
 */
@Service
public class AssignmentHealthService {
    /** Assignments whose vehicle has not left yet can still be reassigned. */
    static final Set<AssignmentStatus> REASSIGNABLE = EnumSet.of(AssignmentStatus.SCHEDULED, AssignmentStatus.AWAITING_RELEASE);
    private final VehicleRepository vehicles;
    private final VehicleRestrictionRepository restrictions;
    private final DriverRepository drivers;
    private final AccountRepository accounts;
    private final DriverEligibility eligibility;
    private final DemoDataPolicy demo;
    public AssignmentHealthService(VehicleRepository vehicles, VehicleRestrictionRepository restrictions, DriverRepository drivers,
            AccountRepository accounts, DriverEligibility eligibility, DemoDataPolicy demo) {
        this.vehicles = vehicles; this.restrictions = restrictions; this.drivers = drivers; this.accounts = accounts;
        this.eligibility = eligibility; this.demo = demo;
    }

    @Transactional(readOnly = true)
    public List<String> problems(Assignment a) {
        if (!REASSIGNABLE.contains(a.getStatus())) return List.of();
        List<String> problems = new ArrayList<>();
        if (a.getVehicleId() != null) vehicles.findById(a.getVehicleId()).ifPresent(v -> {
            if (v.getOperationalStatus() != Vehicle.OperationalStatus.ACTIVE) problems.add(v.getDisplayName() + " is " + label(v.getOperationalStatus()));
            restrictions.findByVehicleIdAndClearedAtIsNull(v.getId()).forEach(r ->
                problems.add(v.getDisplayName() + " has an open " + r.getType().name().toLowerCase(Locale.ROOT) + " restriction: " + r.getReason()));
            if (demo.excludes(v.isDemoData())) problems.add(v.getDisplayName() + " is a development sample record");
        });
        if (a.getDriverId() != null) drivers.findById(a.getDriverId()).ifPresent(d -> {
            boolean enabled = d.getAccountId() != null && accounts.findById(d.getAccountId()).map(Account::isEnabled).orElse(false);
            eligibility.problems(d, enabled, a.getStartsAt().atZoneSameInstant(AssignmentService.OPERATING_ZONE).toLocalDate())
                .forEach(p -> problems.add(d.getName() + ": " + p));
            if (demo.excludes(d.isDemoData())) problems.add(d.getName() + " is a development sample record");
        });
        return problems;
    }

    private static String label(Vehicle.OperationalStatus status) {
        return status == Vehicle.OperationalStatus.OUT_OF_SERVICE ? "out of service" : "retired";
    }
}
