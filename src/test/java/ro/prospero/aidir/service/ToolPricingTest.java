package ro.prospero.aidir.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ro.prospero.aidir.data.ToolPlan;
import ro.prospero.aidir.data.ToolPlan.BillingPeriod;
import ro.prospero.aidir.data.ToolPlan.PaymentType;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ToolPricingTest {

    private static ToolPlan plan(PaymentType type, Integer cents, BillingPeriod period, boolean trial) {
        return new ToolPlan("id", "Plan", type, cents, null, null, period, trial, List.of("Feature"));
    }

    private static ToolPlan free() {
        return plan(PaymentType.FREE, null, null, false);
    }

    private static ToolPlan monthly(int cents) {
        return plan(PaymentType.SUBSCRIPTION, cents, BillingPeriod.MONTHLY, false);
    }

    @ParameterizedTest
    @CsvSource({"999,under_10", "1000,10_50", "4999,10_50", "5000,50_200", "19999,50_200", "20000,200_plus"})
    void bracketsIncludeTheirLowerBound(int cents, String range) {
        assertThat(ToolPricing.of(List.of(monthly(cents))).priceRanges()).containsExactly(range);
    }

    @ParameterizedTest
    @CsvSource({"11999,under_10", "12000,10_50", "24000,10_50", "60000,50_200", "240000,200_plus"})
    void anAnnualPriceCountsAtATwelfth(int cents, String range) {
        ToolPlan annual = plan(PaymentType.SUBSCRIPTION, cents, BillingPeriod.ANNUAL, false);

        ToolPricing.Derived derived = ToolPricing.of(List.of(annual));

        assertThat(derived.priceRanges()).containsExactly(range);
        assertThat(derived.lowestMonthlyCents()).isEqualTo(cents / 12);
    }

    @Test
    void aFreePlanIsItsOwnRangeAndCostsNothing() {
        ToolPricing.Derived derived = ToolPricing.of(List.of(free()));

        assertThat(derived.models()).containsExactly("free");
        assertThat(derived.priceRanges()).containsExactly("free");
        assertThat(derived.lowestMonthlyCents()).isZero();
    }

    @Test
    void contactSalesIsCustomPricingWithNoMonthlyCost() {
        ToolPricing.Derived derived = ToolPricing.of(List.of(plan(PaymentType.CONTACT_SALES, null, null, false)));

        assertThat(derived.models()).containsExactly("contact_sales");
        assertThat(derived.priceRanges()).containsExactly("custom");
        assertThat(derived.lowestMonthlyCents()).isNull();
    }

    @Test
    void payAsYouGoAndOneTimeMatchNoRange() {
        ToolPlan tokens = new ToolPlan("id", "API", PaymentType.PAY_AS_YOU_GO, null, new BigDecimal("0.075"),
                                       new BigDecimal("0.3"), null, false, List.of("Feature"));
        ToolPlan lifetime = plan(PaymentType.ONE_TIME, 29_900, null, false);

        ToolPricing.Derived derived = ToolPricing.of(List.of(tokens, lifetime));

        assertThat(derived.models()).containsExactly("pay_as_you_go", "one_time");
        assertThat(derived.priceRanges()).isEmpty();
        assertThat(derived.lowestMonthlyCents()).isNull();
    }

    @Test
    void aToolMatchesEveryRangeOneOfItsPlansFallsIn() {
        ToolPricing.Derived derived = ToolPricing.of(List.of(monthly(9_900), free(), monthly(2_900)));

        assertThat(derived.models()).containsExactly("free", "subscription");
        assertThat(derived.priceRanges()).containsExactly("free", "10_50", "50_200");
        assertThat(derived.lowestMonthlyCents()).isZero();
    }

    @Test
    void aFreeTrialOnAnyPlanIsAPricingModel() {
        ToolPlan trial = plan(PaymentType.SUBSCRIPTION, 2_900, BillingPeriod.MONTHLY, true);

        assertThat(ToolPricing.of(List.of(trial)).models()).containsExactly("subscription", "free_trial");
    }
}
