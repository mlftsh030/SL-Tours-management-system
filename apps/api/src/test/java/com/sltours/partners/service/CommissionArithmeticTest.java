package com.sltours.partners.service;
import com.sltours.partners.model.PartnerEnums.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Exact commission arithmetic in cents. Rates and amounts here are test inputs only. */
class CommissionArithmeticTest {
    private CommissionService.Agreement percent(int bp, Integer cap, EligibleBasis basis) {
        return new CommissionService.Agreement(UUID.randomUUID(), "T", LocalDate.of(2026, 1, 1), null, CommissionType.PERCENTAGE, bp, null, basis,
            Set.of("TOUR"), 30, RefundTreatment.PROPORTIONAL, null, cap);
    }
    private CommissionService.BookingRow booking(int total, int netPaid) {
        return new CommissionService.BookingRow(UUID.randomUUID(), "SLT", "COMPLETED", "TOUR", total, netPaid, OffsetDateTime.now(), false, "g@example.com",
            UUID.randomUUID(), OffsetDateTime.now());
    }

    @Test void percentagesRoundHalfUpToTheCentWithoutFloatingPointError() {
        assertThat(CommissionService.calculate(1005, percent(1250, null, EligibleBasis.NET_AMOUNT_PAID))).isEqualTo(126); // 125.625 -> 126
        assertThat(CommissionService.calculate(1004, percent(1250, null, EligibleBasis.NET_AMOUNT_PAID))).isEqualTo(126); // 125.5 -> 126
        assertThat(CommissionService.calculate(1003, percent(1250, null, EligibleBasis.NET_AMOUNT_PAID))).isEqualTo(125); // 125.375 -> 125
        assertThat(CommissionService.calculate(99_999_999, percent(333, null, EligibleBasis.NET_AMOUNT_PAID))).isEqualTo(3_330_000); // 3 329 999.97
        assertThat(CommissionService.calculate(3, percent(1, null, EligibleBasis.NET_AMOUNT_PAID))).isZero();
    }

    @Test void capsFixedAmountsAndBasisFollowTheAgreement() {
        assertThat(CommissionService.calculate(1_000_000, percent(1000, 50_000, EligibleBasis.NET_AMOUNT_PAID))).isEqualTo(50_000);
        var fixed = new CommissionService.Agreement(UUID.randomUUID(), "F", LocalDate.of(2026, 1, 1), null, CommissionType.FIXED_PER_BOOKING, null, 7_500,
            EligibleBasis.BOOKING_TOTAL, Set.of("TOUR"), 30, RefundTreatment.FULL_REVERSAL, null, null);
        assertThat(CommissionService.calculate(123_456, fixed)).isEqualTo(7_500);
        assertThat(CommissionService.basisAmount(booking(100_000, 120_000), percent(1000, null, EligibleBasis.NET_AMOUNT_PAID))).isEqualTo(100_000);
        assertThat(CommissionService.basisAmount(booking(100_000, 80_000), percent(1000, null, EligibleBasis.NET_AMOUNT_PAID))).isEqualTo(80_000);
        assertThat(CommissionService.basisAmount(booking(100_000, 80_000), percent(1000, null, EligibleBasis.BOOKING_TOTAL))).isEqualTo(100_000);
    }

    @Test void proportionalRefundAdjustmentsRoundHalfUp() {
        assertThat(CommissionService.proportional(10_000, 2_500, 100_000)).isEqualTo(250);
        assertThat(CommissionService.proportional(333, 1, 2)).isEqualTo(167); // 166.5 -> 167
        assertThat(CommissionService.proportional(333, 2, 2)).isEqualTo(333);
    }
}
