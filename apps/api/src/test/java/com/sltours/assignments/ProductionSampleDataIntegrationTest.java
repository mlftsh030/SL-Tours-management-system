package com.sltours.assignments;
import com.sltours.assignments.service.*;
import com.sltours.drivers.model.Driver;
import com.sltours.drivers.repository.DriverRepository;
import com.sltours.fleet.model.*;
import com.sltours.fleet.repository.VehicleRepository;
import com.sltours.operations.service.OperationsService;
import com.sltours.privatetrips.dto.PrivateTripDtos.VehicleCard;
import com.sltours.privatetrips.service.PrivateTripService;
import com.sltours.support.*;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import static org.assertj.core.api.Assertions.*;

/** Production behaviour for the V2 sample rows flagged by V12. */
@TestPropertySource(properties = "sltours.demo-data.force-exclude=true")
@WithMockUser(username = "admin@test.local", roles = "ADMIN")
class ProductionSampleDataIntegrationTest extends PostgresIntegrationTest {
    @Autowired Fixtures fixtures;
    @Autowired JdbcTemplate jdbc;
    @Autowired VehicleRepository vehicles;
    @Autowired DriverRepository drivers;
    @Autowired PrivateTripService trips;
    @Autowired AvailabilityService availability;
    @Autowired OperationsService operations;
    private static final UUID SEED_VAN = UUID.fromString("70000000-0000-0000-0000-000000000001");
    private static final UUID SEED_DRIVER = UUID.fromString("60000000-0000-0000-0000-000000000001");

    @Test void v12FlagsOnlyTheFixedSampleRowsAndKeepsTheCatalogue() {
        assertThat(jdbc.queryForObject("select count(*) from vehicles where demo_data", Long.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from bookings where demo_data", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from enquiries where demo_data", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from tours", Long.class)).isGreaterThanOrEqualTo(9);
    }

    @Test void sampleVehiclesAndDriversNeverAppearAsInventory() {
        Vehicle seed = vehicles.findById(SEED_VAN).orElseThrow();
        seed.setListingStatus(Vehicle.ListingStatus.LISTED); vehicles.saveAndFlush(seed);
        LocalDate date = LocalDate.now().plusDays(70);
        assertThat(trips.fleet().vehicles()).extracting(VehicleCard::id).doesNotContain(SEED_VAN);
        assertThat(trips.search(date, LocalTime.of(10, 0), 180, 2).vehicles()).extracting(VehicleCard::id).doesNotContain(SEED_VAN);
        OffsetDateTime start = date.atTime(10, 0).atZone(AssignmentService.OPERATING_ZONE).toOffsetDateTime();
        assertThat(availability.vehicles(start, start.plusHours(3), 2, false)).filteredOn(o -> o.vehicle().getId().equals(SEED_VAN))
            .singleElement().satisfies(o -> assertThat(o.reasons()).contains("Development sample record"));
        Driver real = fixtures.driver(true);
        assertThatThrownBy(() -> fixtures.assigned(seed, real, 70)).hasMessageContaining("development sample record");
        Vehicle realVan = fixtures.vehicle(VehicleCategory.VAN, 7, Vehicle.ListingStatus.HIDDEN);
        assertThatThrownBy(() -> fixtures.assigned(realVan, drivers.findById(SEED_DRIVER).orElseThrow(), 71)).hasMessageContaining("development sample record");
        Vehicle restore = vehicles.findById(SEED_VAN).orElseThrow(); restore.setListingStatus(Vehicle.ListingStatus.HIDDEN); vehicles.saveAndFlush(restore);
    }

    @Test void dashboardFiguresLeaveOutSampleBookings() {
        long withSamples = jdbc.queryForObject("select count(*) from bookings where status = 'CONFIRMED'", Long.class);
        long sampleConfirmed = jdbc.queryForObject("select count(*) from bookings where status = 'CONFIRMED' and demo_data", Long.class);
        assertThat(operations.dashboard().confirmedBookings()).isEqualTo(withSamples - sampleConfirmed);
    }
}
