package com.sltours.privatetrips;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.sltours.fleet.model.Vehicle;
import com.sltours.fleet.model.VehicleCategory;
import com.sltours.privatetrips.model.PrivateTripRate;
import com.sltours.privatetrips.repository.PrivateTripRateRepository;
import com.sltours.privatetrips.service.PrivateTripService;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Outside dev/test (where ProductionSafetyCheck forbids enabling sample rates) a SAMPLE category rate must never become a
 * price: the vehicle resolves to no rate, so it is shown as "Priced on request" and submit() refuses it in favour of a quote.
 */
class SampleRatePolicyTest {
    private final PrivateTripRateRepository rates = mock(PrivateTripRateRepository.class);

    private PrivateTripService service(boolean allowSampleRates) {
        return new PrivateTripService(null, null, null, rates, null, null, null, null, null, null, null, null, allowSampleRates, null, null, null);
    }

    private Vehicle vehicleWithoutOwnRate() {
        Vehicle vehicle = new Vehicle();
        vehicle.setCategory(VehicleCategory.SEDAN);
        vehicle.setHourlyRateCents(null);
        return vehicle;
    }

    private void categoryRate(PrivateTripRate.Status status) {
        PrivateTripRate rate = mock(PrivateTripRate.class);
        when(rate.getHourlyRateCents()).thenReturn(45_000);
        when(rate.getMinimumMinutes()).thenReturn(120);
        when(rate.getRateStatus()).thenReturn(status);
        when(rates.findById(VehicleCategory.SEDAN)).thenReturn(Optional.of(rate));
    }

    @Test void sampleRateIsNotAPriceWhenSampleRatesAreDisabled() {
        categoryRate(PrivateTripRate.Status.SAMPLE);
        assertThat(service(false).resolve(vehicleWithoutOwnRate(), false)).isEmpty();
    }

    @Test void sampleRateIsLabelledAsSampleInDevelopment() {
        categoryRate(PrivateTripRate.Status.SAMPLE);
        assertThat(service(true).resolve(vehicleWithoutOwnRate(), true)).hasValueSatisfying(r -> assertThat(r.sample()).isTrue());
    }

    @Test void liveRateIsAPriceInEveryEnvironment() {
        categoryRate(PrivateTripRate.Status.LIVE);
        assertThat(service(false).resolve(vehicleWithoutOwnRate(), false))
            .hasValueSatisfying(r -> { assertThat(r.sample()).isFalse(); assertThat(r.hourlyRateCents()).isEqualTo(45_000); });
    }

    @Test void unknownRateIsNeverZero() {
        when(rates.findById(VehicleCategory.SEDAN)).thenReturn(Optional.empty());
        assertThat(service(false).resolve(vehicleWithoutOwnRate(), false)).isEmpty();
    }
}
