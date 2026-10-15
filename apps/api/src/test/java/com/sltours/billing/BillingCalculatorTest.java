package com.sltours.billing;
import com.sltours.billing.model.BillingTypes.*;
import com.sltours.billing.render.MoneyFormat;
import com.sltours.billing.service.BillingCalculator;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BillingCalculatorTest {
    static Line line(String qty, long unit, LineKind kind) {
        return new Line("x", List.of(), null, new BigDecimal(qty), null, unit, kind, null, null, null);
    }
    static DocumentContent doc(Discount discount, boolean tax, Line... lines) {
        return new DocumentContent(new Customer("C", null, null, null), null, null, null, null, null, "ZAR", null, null, null, List.of(lines),
            discount, tax, null, null, null);
    }

    @Test void theReferenceInvoiceTotalsR2850() {
        Totals t = BillingCalculator.compute(BillingFixtures.youngLife(), null);
        assertThat(t.total()).isEqualTo(285_000);
        assertThat(t.discount()).isZero();
        assertThat(t.tax()).isZero();
        assertThat(t.taxRateBasisPoints()).isNull();
    }

    @Test void lineTotalsRoundHalfUpToTheCent() {
        Totals t = BillingCalculator.compute(doc(null, false, line("2.5", 333, LineKind.SERVICE), line("0.01", 50, LineKind.SERVICE)), null);
        assertThat(t.lineTotals()).containsExactly(833L, 1L);   // 832.5 -> 833, 0.5 -> 1
        assertThat(t.total()).isEqualTo(834);
    }

    @Test void percentageDiscountAndTaxRoundHalfUpInOrder() {
        Totals t = BillingCalculator.compute(doc(new Discount(DiscountType.PERCENT, 1250, "Group"), true, line("1", 99_999, LineKind.SERVICE)),
            new BillingCalculator.TaxRule(1500, "VAT"));
        assertThat(t.subtotal()).isEqualTo(99_999);
        assertThat(t.discount()).isEqualTo(12_500);             // 12 499.875 -> 12 500
        assertThat(t.taxable()).isEqualTo(87_499);
        assertThat(t.tax()).isEqualTo(13_125);                  // 13 124.85 -> 13 125
        assertThat(t.total()).isEqualTo(100_624);
        assertThat(t.taxLabel()).isEqualTo("VAT");
    }

    @Test void depositAdjustmentsMayBeNegativeButNotServicesOrTheSubtotal() {
        Totals deposit = BillingCalculator.compute(doc(null, false, line("1", 4_580_000, LineKind.SERVICE), line("1", -2_290_000, LineKind.ADJUSTMENT)), null);
        assertThat(deposit.total()).isEqualTo(2_290_000);
        assertThatThrownBy(() -> BillingCalculator.compute(doc(null, false, line("1", -100, LineKind.SERVICE)), null)).hasMessageContaining("negative price");
        assertThatThrownBy(() -> BillingCalculator.compute(doc(null, false, line("1", 100, LineKind.SERVICE), line("1", -200, LineKind.ADJUSTMENT)), null))
            .hasMessageContaining("Adjustments cannot be larger");
    }

    @Test void discountsAreBoundedAndTaxNeedsAConfiguredRate() {
        assertThatThrownBy(() -> BillingCalculator.compute(doc(new Discount(DiscountType.AMOUNT, 1_001, null), false, line("1", 1_000, LineKind.SERVICE)), null))
            .hasMessageContaining("more than the subtotal");
        assertThatThrownBy(() -> BillingCalculator.compute(doc(new Discount(DiscountType.PERCENT, 10_001, null), false, line("1", 1_000, LineKind.SERVICE)), null))
            .hasMessageContaining("100%");
        assertThatThrownBy(() -> BillingCalculator.compute(doc(null, true, line("1", 1_000, LineKind.SERVICE)), null)).hasMessageContaining("Tax is not configured");
        assertThatThrownBy(() -> BillingCalculator.compute(doc(null, false, line("1", BillingCalculator.MAX_UNIT_PRICE_CENTS + 1, LineKind.SERVICE)), null))
            .hasMessageContaining("too large");
    }

    @Test void amountsPrintLikeTheSlToursTemplate() {
        assertThat(MoneyFormat.withCurrency(285_000, "ZAR")).isEqualTo("R2 850");
        assertThat(MoneyFormat.amount(4_580_000)).isEqualTo("45 800");
        assertThat(MoneyFormat.amount(-2_290_000)).isEqualTo("(22 900)");
        assertThat(MoneyFormat.amount(123_456_789)).isEqualTo("1 234 567,89");
        assertThat(MoneyFormat.withCurrency(120_050, "USD")).isEqualTo("USD 1 200,50");
    }
}
