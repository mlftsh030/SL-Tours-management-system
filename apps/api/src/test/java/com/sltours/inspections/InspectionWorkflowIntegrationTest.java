package com.sltours.inspections;
import com.sltours.assignments.model.*;
import com.sltours.assignments.repository.AssignmentRepository;
import com.sltours.assignments.service.*;
import com.sltours.drivers.model.Driver;
import com.sltours.fleet.model.*;
import com.sltours.fleet.repository.VehicleRestrictionRepository;
import com.sltours.fleet.service.RestrictionService;
import com.sltours.inspections.dto.InspectionDtos.*;
import com.sltours.inspections.model.*;
import com.sltours.inspections.service.InspectionService;
import com.sltours.shared.error.*;
import com.sltours.support.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import static com.sltours.support.Fixtures.*;
import static org.assertj.core.api.Assertions.*;

@WithMockUser(username = "admin@test.local", roles = "ADMIN")
class InspectionWorkflowIntegrationTest extends PostgresIntegrationTest {
    @Autowired Fixtures fixtures;
    @Autowired InspectionService inspections;
    @Autowired AssignmentRepository assignments;
    @Autowired RestrictionService restrictionService;
    @Autowired VehicleRestrictionRepository restrictions;
    @Autowired AvailabilityService availability;
    @Autowired DriverAssignmentService driverViews;

    private Assignment reload(Assignment a) { return assignments.findById(a.getId()).orElseThrow(); }
    private SubmissionResult collect(Driver driver, Assignment a, InspectionForm form) {
        return inspections.submitCollection(driver, a.getId(), form, photosFor(form), photo("selfie"));
    }

    @Test void driversCannotSeeOrActOnAnotherDriversAssignmentOrMedia() {
        Vehicle vehicle = fixtures.vehicle(VehicleCategory.VAN, 7, Vehicle.ListingStatus.HIDDEN);
        Driver owner = fixtures.driver(true), other = fixtures.driver(true);
        Assignment assignment = fixtures.assigned(vehicle, owner, 3);
        InspectionForm form = form(1000, Map.of());

        assertThatThrownBy(() -> driverViews.get(other, assignment.getId())).isInstanceOf(ResourceNotFoundException.class);
        assertThat(driverViews.list(other)).noneMatch(s -> s.id().equals(assignment.getId()));
        assertThatThrownBy(() -> collect(other, assignment, form)).isInstanceOf(ResourceNotFoundException.class);

        SubmissionResult result = collect(owner, assignment, form);
        InspectionView view = inspections.get(result.inspectionId());
        UUID selfie = UUID.fromString(view.selfieUrl().substring(view.selfieUrl().lastIndexOf('/') + 1));
        assertThat(inspections.driverMayView(owner, selfie)).isTrue();
        assertThat(inspections.driverMayView(owner, view.photos().get(0).mediaId())).isTrue();
        assertThat(inspections.driverMayView(other, selfie)).isFalse();
        assertThat(inspections.driverMayView(other, view.photos().get(0).mediaId())).isFalse();
        assertThat(driverViews.get(owner, assignment.getId()).collection().state()).isEqualTo("AWAITING_APPROVAL");
    }

    @Test void collectionRequiresEligibleDriverSelfieCompleteChecklistAndPhotos() {
        Vehicle vehicle = fixtures.vehicle(VehicleCategory.SEDAN, 3, Vehicle.ListingStatus.HIDDEN);
        Driver driver = fixtures.driver(true);
        Assignment assignment = fixtures.assigned(vehicle, driver, 4);
        InspectionForm form = form(500, Map.of());
        assertThatThrownBy(() -> inspections.submitCollection(driver, assignment.getId(), form, photosFor(form), null))
            .hasMessageContaining("selfie");
        assertThatThrownBy(() -> inspections.submitCollection(driver, assignment.getId(), form, photos(3), photo("selfie")))
            .isInstanceOf(IllegalArgumentException.class);
        var incomplete = new InspectionForm(500, FuelLevel.HALF, null, form.checklist().subList(1, form.checklist().size()), form.photoTags());
        assertThatThrownBy(() -> collect(driver, assignment, incomplete)).hasMessageContaining("Complete every checklist item");
        var noDefectPhoto = form(500, Map.of(ChecklistItem.MIRRORS, ChecklistAnswer.Severity.MINOR));
        var withoutPhoto = new InspectionForm(500, FuelLevel.HALF, null, noDefectPhoto.checklist(), List.of("VEHICLE", "VEHICLE", "VEHICLE", "VEHICLE"));
        assertThatThrownBy(() -> inspections.submitCollection(driver, assignment.getId(), withoutPhoto, photos(4), photo("selfie")))
            .hasMessageContaining("Add a photo of the defect");
        var disguised = new org.springframework.mock.web.MockMultipartFile("selfie", "selfie.jpg", "image/jpeg", "not really an image".getBytes());
        assertThatThrownBy(() -> inspections.submitCollection(driver, assignment.getId(), form, photosFor(form), disguised))
            .hasMessageContaining("JPEG, PNG or WebP");
        assertThat(reload(assignment).getStatus()).isEqualTo(AssignmentStatus.SCHEDULED);
    }

    @Test void approvalReleasesTheVehicleButNeverClearsAMaintenanceRestriction() {
        Vehicle vehicle = fixtures.vehicle(VehicleCategory.VAN, 7, Vehicle.ListingStatus.HIDDEN);
        Driver driver = fixtures.driver(true);
        Assignment assignment = fixtures.assigned(vehicle, driver, 5);
        SubmissionResult submitted = collect(driver, assignment, form(1000, Map.of()));
        assertThat(submitted.assignmentStatus()).isEqualTo(AssignmentStatus.AWAITING_RELEASE);
        assertThatThrownBy(() -> inspections.submitReturn(driver, assignment.getId(), form(1100, Map.of()), photos(4)))
            .hasMessageContaining("not been released");

        // A maintenance issue reported by staff after collection blocks approval.
        VehicleRestriction restriction = restrictionService.open(vehicle.getId(), VehicleRestriction.Type.MAINTENANCE, "Service light came on", null);
        long version = inspections.get(submitted.inspectionId()).version();
        assertThatThrownBy(() -> inspections.approve(submitted.inspectionId(), new ReviewRequest(version, "ok")))
            .hasMessageContaining("Approval does not clear restrictions");
        assertThat(restrictions.findById(restriction.getId()).orElseThrow().isOpen()).isTrue();

        assertThatThrownBy(() -> restrictionService.clear(vehicle.getId(), restriction.getId(), 0, "done"))
            .hasMessageContaining("at least 10 characters");
        restrictionService.clear(vehicle.getId(), restriction.getId(), 0, "Serviced at the dealer, light reset and road tested");
        InspectionView approved = inspections.approve(submitted.inspectionId(), new ReviewRequest(version, "Looks good"));
        assertThat(approved.reviewStatus()).isEqualTo(Inspection.ReviewStatus.APPROVED);
        assertThat(reload(assignment).getStatus()).isEqualTo(AssignmentStatus.RELEASED);
    }

    @Test void safetyDefectsBlockCollectionApprovalAvailabilityAndAssignment() {
        Vehicle vehicle = fixtures.vehicle(VehicleCategory.MINIBUS, 13, Vehicle.ListingStatus.LISTED);
        Driver driver = fixtures.driver(true);
        Assignment assignment = fixtures.assigned(vehicle, driver, 6);
        // Brakes are safety-critical: even a "minor" brake defect is a safety defect.
        SubmissionResult result = collect(driver, assignment, form(2000, Map.of(ChecklistItem.BRAKES, ChecklistAnswer.Severity.MINOR)));
        assertThat(result.outcome()).contains("Do not take this vehicle");
        assertThat(reload(assignment).getStatus()).isEqualTo(AssignmentStatus.SCHEDULED);
        assertThat(restrictions.findByVehicleIdAndClearedAtIsNull(vehicle.getId())).singleElement()
            .satisfies(r -> assertThat(r.getType()).isEqualTo(VehicleRestriction.Type.SAFETY));
        long version = inspections.get(result.inspectionId()).version();
        assertThatThrownBy(() -> inspections.approve(result.inspectionId(), new ReviewRequest(version, null)))
            .hasMessageContaining("safety defect cannot be approved");
        OffsetDateTime later = OffsetDateTime.now().plusDays(30);
        assertThat(availability.vehicles(later, later.plusHours(3), 2, true)).filteredOn(o -> o.vehicle().getId().equals(vehicle.getId()))
            .singleElement().satisfies(o -> assertThat(o.available()).isFalse());
        Driver another = fixtures.driver(true);
        assertThatThrownBy(() -> fixtures.assigned(vehicle, another, 30)).hasMessageContaining("open safety or maintenance restriction");
        assertThat(driverViews.get(driver, assignment.getId()).canCollect()).isFalse();
    }

    @Test void returnNeedsCollectionAndCannotRollBackTheOdometerThenComparesConditions() {
        Vehicle vehicle = fixtures.vehicle(VehicleCategory.SUV, 5, Vehicle.ListingStatus.HIDDEN);
        Driver driver = fixtures.driver(true);
        Assignment assignment = fixtures.assigned(vehicle, driver, 7);
        assertThatThrownBy(() -> inspections.submitReturn(driver, assignment.getId(), form(10, Map.of()), photos(4)))
            .hasMessageContaining("No collection inspection");

        SubmissionResult collected = collect(driver, assignment, form(42_000, Map.of(ChecklistItem.INTERIOR, ChecklistAnswer.Severity.MINOR)));
        inspections.approve(collected.inspectionId(), new ReviewRequest(inspections.get(collected.inspectionId()).version(), null));
        assertThatThrownBy(() -> inspections.submitReturn(driver, assignment.getId(), form(41_999, Map.of()), photos(4)))
            .hasMessageContaining("cannot be lower than at collection");

        InspectionForm returnForm = form(42_180, Map.of(ChecklistItem.INTERIOR, ChecklistAnswer.Severity.MINOR,
            ChecklistItem.BODY_EXTERIOR, ChecklistAnswer.Severity.MINOR));
        SubmissionResult returned = inspections.submitReturn(driver, assignment.getId(), returnForm, photosFor(returnForm));
        assertThat(returned.assignmentStatus()).isEqualTo(AssignmentStatus.RETURNED);
        assertThatThrownBy(() -> inspections.submitReturn(driver, assignment.getId(), returnForm, photosFor(returnForm)))
            .isInstanceOf(IllegalArgumentException.class);

        Comparison comparison = inspections.compare(assignment.getId());
        assertThat(comparison.distanceKm()).isEqualTo(180);
        assertThat(comparison.newlyReportedDefects()).extracting(ChecklistLine::item).containsExactly(ChecklistItem.BODY_EXTERIOR);
        assertThat(comparison.persistingDefects()).extracting(ChecklistLine::item).containsExactly(ChecklistItem.INTERIOR);

        // New damage keeps the vehicle out of service until an administrator clears it, even after the return is approved.
        inspections.approve(returned.inspectionId(), new ReviewRequest(inspections.get(returned.inspectionId()).version(), "Damage noted"));
        assertThat(reload(assignment).getStatus()).isEqualTo(AssignmentStatus.CLOSED);
        assertThat(restrictionService.isRestricted(vehicle.getId())).isTrue();
    }

    @Test void ineligibleOrUnverifiedDriversCannotBeAssignedOrCollect() {
        Vehicle vehicle = fixtures.vehicle(VehicleCategory.SEDAN, 3, Vehicle.ListingStatus.HIDDEN);
        Driver unverified = fixtures.driver(false);
        assertThatThrownBy(() -> fixtures.assigned(vehicle, unverified, 8)).hasMessageContaining("verification is not complete");
    }

    @Test void rejectedCollectionCanBeResubmittedAndAssignmentChangesSupersedeIt() {
        Vehicle first = fixtures.vehicle(VehicleCategory.VAN, 7, Vehicle.ListingStatus.HIDDEN);
        Vehicle second = fixtures.vehicle(VehicleCategory.VAN, 7, Vehicle.ListingStatus.HIDDEN);
        Driver driver = fixtures.driver(true);
        Assignment assignment = fixtures.assigned(first, driver, 9);
        SubmissionResult submitted = collect(driver, assignment, form(100, Map.of()));
        assertThatThrownBy(() -> inspections.reject(submitted.inspectionId(), new ReviewRequest(inspections.get(submitted.inspectionId()).version(), " ")))
            .hasMessageContaining("Explain why");
        inspections.reject(submitted.inspectionId(), new ReviewRequest(inspections.get(submitted.inspectionId()).version(), "Photos are blurry"));
        assertThat(driverViews.get(driver, assignment.getId()).collection().state()).isEqualTo("RESUBMIT");
        SubmissionResult again = collect(driver, assignment, form(100, Map.of()));
        assertThat(again.assignmentStatus()).isEqualTo(AssignmentStatus.AWAITING_RELEASE);
        Assignment current = reload(assignment);
        assertThatCode(() -> fixtures.assigned(first, fixtures.driver(true), 9)).isInstanceOf(ConflictException.class);
        AssignmentService service = assignmentService;
        service.change(current.getId(), current.getVersion(), second.getId(), driver.getId(), 2);
        assertThat(inspections.get(again.inspectionId()).reviewStatus()).isEqualTo(Inspection.ReviewStatus.REJECTED);
        assertThat(reload(assignment).getStatus()).isEqualTo(AssignmentStatus.SCHEDULED);
    }
    @Autowired AssignmentService assignmentService;
    @Autowired com.sltours.bookings.repository.BookingRepository bookings;

    @Test void aVehicleStillOutOnAnotherTripCannotBeCollectedOrReleasedAgain() {
        Vehicle vehicle = fixtures.vehicle(VehicleCategory.VAN, 7, Vehicle.ListingStatus.HIDDEN);
        Driver driver = fixtures.driver(true), next = fixtures.driver(true);
        Assignment first = fixtures.assigned(vehicle, driver, 10);
        Assignment later = fixtures.assigned(vehicle, next, 11);
        String firstTrip = bookings.findById(first.getBookingId()).orElseThrow().getReference();

        // Waiting for release already counts as out: the vehicle is with the first driver.
        SubmissionResult collected = collect(driver, first, form(5000, Map.of()));
        assertThatThrownBy(() -> collect(next, later, form(5000, Map.of())))
            .isInstanceOf(ConflictException.class).hasMessageContaining("not been returned from trip " + firstTrip);
        var view = driverViews.get(next, later.getId());
        assertThat(view.canCollect()).isFalse();
        assertThat(view.blockers()).anyMatch(b -> b.contains(firstTrip));

        inspections.approve(collected.inspectionId(), new ReviewRequest(inspections.get(collected.inspectionId()).version(), null));
        assertThatThrownBy(() -> collect(next, later, form(5000, Map.of()))).hasMessageContaining("not been returned from trip " + firstTrip);
        assertThat(reload(later).getStatus()).isEqualTo(AssignmentStatus.SCHEDULED);

        // Once the return is recorded the vehicle is back, and the next trip can collect it.
        inspections.submitReturn(driver, first.getId(), form(5100, Map.of()), photos(4));
        assertThat(driverViews.get(next, later.getId()).canCollect()).isTrue();
        SubmissionResult nextCollection = collect(next, later, form(5100, Map.of()));
        assertThat(nextCollection.assignmentStatus()).isEqualTo(AssignmentStatus.AWAITING_RELEASE);

        // Records from before this rule (the first trip still released) block the release instead of doubling it.
        Assignment stillOut = reload(first);
        stillOut.setStatus(AssignmentStatus.RELEASED);
        assignments.saveAndFlush(stillOut);
        long version = inspections.get(nextCollection.inspectionId()).version();
        assertThatThrownBy(() -> inspections.approve(nextCollection.inspectionId(), new ReviewRequest(version, null)))
            .isInstanceOf(ConflictException.class).hasMessageContaining("still released on trip " + firstTrip);
        assertThat(reload(later).getStatus()).isEqualTo(AssignmentStatus.AWAITING_RELEASE);
    }
}
