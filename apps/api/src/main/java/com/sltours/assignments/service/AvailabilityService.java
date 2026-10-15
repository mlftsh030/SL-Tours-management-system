package com.sltours.assignments.service;
import com.sltours.accounts.model.Account;
import com.sltours.accounts.repository.AccountRepository;
import com.sltours.assignments.repository.AssignmentRepository;
import com.sltours.drivers.model.Driver;
import com.sltours.drivers.repository.DriverRepository;
import com.sltours.drivers.service.DriverEligibility;
import com.sltours.fleet.model.Vehicle;
import com.sltours.fleet.repository.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Derives availability from operational status, restrictions, capacity and assignments (including turnaround). */
@Service
public class AvailabilityService {
    public record VehicleOption(Vehicle vehicle, List<String> reasons) { public boolean available() { return reasons.isEmpty(); } }
    public record DriverOption(Driver driver, List<String> reasons) { public boolean available() { return reasons.isEmpty(); } }
    private final VehicleRepository vehicles;
    private final VehicleRestrictionRepository restrictions;
    private final AssignmentRepository assignments;
    private final DriverRepository drivers;
    private final AccountRepository accounts;
    private final DriverEligibility eligibility;
    private final OperationsSettingsRepository settings;
    private final com.sltours.shared.demo.DemoDataPolicy demo;
    public AvailabilityService(VehicleRepository vehicles, VehicleRestrictionRepository restrictions, AssignmentRepository assignments,
            DriverRepository drivers, AccountRepository accounts, DriverEligibility eligibility, OperationsSettingsRepository settings,
            com.sltours.shared.demo.DemoDataPolicy demo) {
        this.vehicles = vehicles; this.restrictions = restrictions; this.assignments = assignments; this.drivers = drivers;
        this.accounts = accounts; this.eligibility = eligibility; this.settings = settings; this.demo = demo;
    }

    /** Every vehicle with the reasons it cannot take a trip in [start, end); public listings skip hidden vehicles. */
    @Transactional(readOnly = true)
    public List<VehicleOption> vehicles(OffsetDateTime start, OffsetDateTime end, int passengers, boolean listedOnly) {
        OffsetDateTime blockedUntil = end.plusMinutes(settings.current().getTurnaroundMinutes());
        Set<UUID> busy = assignments.busyVehicleIds(start, blockedUntil);
        Set<UUID> restricted = restrictions.findRestrictedVehicleIds();
        List<VehicleOption> options = new ArrayList<>();
        for (Vehicle v : vehicles.findAllByOrderByDisplayNameAsc()) {
            if (listedOnly && (v.getListingStatus() != Vehicle.ListingStatus.LISTED || demo.excludes(v.isDemoData()))) continue;
            List<String> reasons = new ArrayList<>();
            if (demo.excludes(v.isDemoData())) reasons.add("Development sample record");
            if (v.getOperationalStatus() != Vehicle.OperationalStatus.ACTIVE) reasons.add("Not in operational service");
            if (restricted.contains(v.getId())) reasons.add("Open safety or maintenance restriction");
            if (v.getCapacity() < passengers) reasons.add("Seats " + v.getCapacity());
            if (busy.contains(v.getId())) reasons.add("Already assigned (including turnaround)");
            options.add(new VehicleOption(v, reasons));
        }
        return options;
    }

    @Transactional(readOnly = true)
    public List<DriverOption> drivers(OffsetDateTime start, OffsetDateTime end) {
        OffsetDateTime blockedUntil = end.plusMinutes(settings.current().getTurnaroundMinutes());
        List<DriverOption> options = new ArrayList<>();
        for (Driver d : drivers.findAllByOrderByNameAsc()) {
            boolean enabled = d.getAccountId() != null && accounts.findById(d.getAccountId()).map(Account::isEnabled).orElse(false);
            List<String> reasons = new ArrayList<>(eligibility.problems(d, enabled, start.atZoneSameInstant(AssignmentService.OPERATING_ZONE).toLocalDate()));
            if (demo.excludes(d.isDemoData())) reasons.add("Development sample record");
            if (!assignments.driverConflicts(d.getId(), start, blockedUntil, AssignmentRepository.NONE).isEmpty())
                reasons.add("Already assigned (including turnaround)");
            options.add(new DriverOption(d, reasons));
        }
        return options;
    }
}
