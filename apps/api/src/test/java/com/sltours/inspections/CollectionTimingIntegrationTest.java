package com.sltours.inspections;
import com.sltours.assignments.model.*;
import com.sltours.assignments.service.DriverAssignmentService;
import com.sltours.drivers.model.Driver;
import com.sltours.fleet.model.*;
import com.sltours.inspections.service.InspectionService;
import com.sltours.support.*;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import static com.sltours.support.Fixtures.*;
import static org.assertj.core.api.Assertions.*;

/** Runs with the production collection window: a vehicle can be collected only from 12 hours before the trip. */
@WithMockUser(username = "admin@test.local", roles = "ADMIN")
@TestPropertySource(properties = "sltours.inspections.collection-opens-before=12h")
class CollectionTimingIntegrationTest extends PostgresIntegrationTest {
    @Autowired Fixtures fixtures;
    @Autowired InspectionService inspections;
    @Autowired DriverAssignmentService driverViews;

    @Test void collectionOpensTwelveHoursBeforeTheTrip() {
        Vehicle vehicle = fixtures.vehicle(VehicleCategory.SEDAN, 3, Vehicle.ListingStatus.HIDDEN);
        Driver driver = fixtures.driver(true);
        Assignment farAhead = fixtures.assigned(vehicle, driver, 20);
        var form = form(800, Map.of());

        assertThatThrownBy(() -> inspections.submitCollection(driver, farAhead.getId(), form, photosFor(form), photo("selfie")))
            .hasMessageContaining("Collection opens at 21:00 on").hasMessageContaining("12 hours before the trip");
        var view = driverViews.get(driver, farAhead.getId());
        assertThat(view.canCollect()).isFalse();
        assertThat(view.blockers()).isEmpty();
        assertThat(view.nextAction()).startsWith("Collection opens at 21:00 on");

        OffsetDateTime soon = OffsetDateTime.now().plusHours(2).truncatedTo(ChronoUnit.MINUTES);
        Assignment today = fixtures.assignedAt(vehicle, driver, soon, soon.plusHours(3));
        assertThat(driverViews.get(driver, today.getId()).canCollect()).isTrue();
        assertThat(inspections.submitCollection(driver, today.getId(), form, photosFor(form), photo("selfie")).assignmentStatus())
            .isEqualTo(AssignmentStatus.AWAITING_RELEASE);
    }
}
