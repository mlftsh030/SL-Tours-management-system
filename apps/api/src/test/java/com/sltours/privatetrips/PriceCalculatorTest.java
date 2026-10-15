package com.sltours.privatetrips;
import com.sltours.privatetrips.service.PriceCalculator;
import com.sltours.privatetrips.service.PriceCalculator.Fee;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PriceCalculatorTest {
    @Test void baseIsHourlyRateTimesBillableHours() {
        var price = PriceCalculator.price(65_000, 240, 180, 60, List.of());
        assertThat(price.billableMinutes()).isEqualTo(240);
        assertThat(price.baseCents()).isEqualTo(260_000);
        assertThat(price.totalCents()).isEqualTo(260_000);
        assertThat(price.minimumApplied()).isFalse();
        assertThat(price.roundedUp()).isFalse();
    }
    @Test void minimumDurationIsChargedAndDisclosed() {
        var price = PriceCalculator.price(45_000, 60, 120, 60, List.of());
        assertThat(price.billableMinutes()).isEqualTo(120);
        assertThat(price.minimumApplied()).isTrue();
        assertThat(price.baseCents()).isEqualTo(90_000);
    }
    @Test void partialHoursRoundUpToTheBillingIncrement() {
        var hourly = PriceCalculator.price(45_000, 150, 120, 60, List.of());
        assertThat(hourly.billableMinutes()).isEqualTo(180);
        assertThat(hourly.roundedUp()).isTrue();
        var halfHourly = PriceCalculator.price(45_000, 150, 120, 30, List.of());
        assertThat(halfHourly.billableMinutes()).isEqualTo(150);
        assertThat(halfHourly.baseCents()).isEqualTo(112_500);
    }
    @Test void feesAreAddedAndPartCentsRoundHalfUpPerLine() {
        // 33 333 c/h for 90 minutes = 49 999.5 c → 50 000 c
        var price = PriceCalculator.price(33_333, 90, 60, 30, List.of(new Fee("Airport access", "FIXED", 5_000),
            new Fee("Driver allowance", "PER_BILLABLE_HOUR", 1_001)));
        assertThat(price.baseCents()).isEqualTo(50_000);
        assertThat(price.fees()).extracting(PriceCalculator.FeeLine::totalCents).containsExactly(5_000, 1_502);
        assertThat(price.feesCents()).isEqualTo(6_502);
        assertThat(price.totalCents()).isEqualTo(56_502);
    }
    @Test void invalidInputsAreRejected() {
        assertThatThrownBy(() -> PriceCalculator.price(0, 60, 60, 60, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PriceCalculator.price(10_000, 0, 60, 60, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PriceCalculator.price(Integer.MAX_VALUE, 1440, 60, 60, List.of())).isInstanceOf(ArithmeticException.class);
    }
}
