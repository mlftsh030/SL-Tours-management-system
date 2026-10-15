package com.sltours.assignments;
import com.sltours.assignments.dto.AssignmentDtos.AdminAssignment;
import com.sltours.assignments.model.Assignment;
import com.sltours.assignments.service.AdminAssignmentService;
import com.sltours.assignments.dto.AssignmentDtos.ChangeAssignmentRequest;
import com.sltours.drivers.dto.DriverDtos.VerificationRequest;
import com.sltours.drivers.model.Driver;
import com.sltours.drivers.service.AdminDriverService;
import com.sltours.fleet.model.*;
import com.sltours.fleet.service.*;
import com.sltours.support.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import static org.assertj.core.api.Assertions.*;

@WithMockUser(username = "admin@test.local", roles = "ADMIN")
class AssignmentAttentionIntegrationTest extends PostgresIntegrationTest {
    @Autowired Fixtures fixtures;
    @Autowired AdminAssignmentService assignments;
    @Autowired RestrictionService restrictions;
    @Autowired FleetService fleet;
    @Autowired AdminDriverService drivers;

    @Test void restrictingAVehicleFlagsItsUpcomingTripsAndReassignmentClearsTheWarning() {
        Vehicle van = fixtures.vehicle(VehicleCategory.VAN, 7, Vehicle.ListingStatus.HIDDEN);
        Vehicle spare = fixtures.vehicle(VehicleCategory.VAN, 7, Vehicle.ListingStatus.HIDDEN);
        Driver driver = fixtures.driver(true);
        Assignment trip = fixtures.assigned(van, driver, 12);
        restrictions.open(van.getId(), VehicleRestriction.Type.SAFETY, "Brake warning light", null);
        AdminAssignment flagged = fleet.get(van.getId()).upcomingAssignments().stream().filter(a -> a.id().equals(trip.getId())).findFirst().orElseThrow();
        assertThat(flagged.problems()).anySatisfy(p -> assertThat(p).contains("open safety restriction"));
        assertThat(assignments.needingAttention()).extracting(AdminAssignment::id).contains(trip.getId());
        // Reassignment goes through the normal conflict checks: the restricted van is refused, the spare is accepted.
        assertThatThrownBy(() -> assignments.change(trip.getId(), new ChangeAssignmentRequest(flagged.version(), van.getId(), driver.getId())))
            .hasMessageContaining("restriction");
        AdminAssignment moved = assignments.change(trip.getId(), new ChangeAssignmentRequest(flagged.version(), spare.getId(), driver.getId()));
        assertThat(moved.problems()).isEmpty();
        assertThat(assignments.needingAttention()).extracting(AdminAssignment::id).doesNotContain(trip.getId());
    }

    @Test void suspendingADriverFlagsTheirUpcomingTrips() {
        Vehicle van = fixtures.vehicle(VehicleCategory.VAN, 7, Vehicle.ListingStatus.HIDDEN);
        Driver driver = fixtures.driver(true);
        Assignment trip = fixtures.assigned(van, driver, 13);
        var detail = drivers.get(driver.getId());
        var suspended = drivers.setVerification(driver.getId(), new VerificationRequest(detail.version(), Driver.Verification.SUSPENDED, "Licence query"));
        assertThat(suspended.upcomingAssignments()).filteredOn(a -> a.id().equals(trip.getId())).singleElement()
            .satisfies(a -> assertThat(a.problems()).anySatisfy(p -> assertThat(p).contains("verification is not complete")));
    }
}
