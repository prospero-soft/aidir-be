package ro.prospero.aidir.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import ro.prospero.aidir.data.ToolPlan.BillingPeriod;
import ro.prospero.aidir.data.ToolPlan.PaymentType;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ToolPlanValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static ToolPlan plan(PaymentType type, Integer cents, BigDecimal input, BillingPeriod period,
                                 boolean trial) {
        return new ToolPlan("id", "Plan", type, cents, input, null, period, trial, List.of("Feature"));
    }

    private boolean valid(ToolPlan plan) {
        return validator.validate(plan).isEmpty();
    }

    @Test
    void aFreePlanHasNoPriceAndNoTrial() {
        assertThat(valid(plan(PaymentType.FREE, null, null, null, false))).isTrue();
        assertThat(valid(plan(PaymentType.FREE, 100, null, null, false))).isFalse();
        assertThat(valid(plan(PaymentType.FREE, null, null, null, true))).isFalse();
    }

    @Test
    void aSubscriptionNeedsAPriceAndAPeriod() {
        assertThat(valid(plan(PaymentType.SUBSCRIPTION, 2_900, null, BillingPeriod.ANNUAL, true))).isTrue();
        assertThat(valid(plan(PaymentType.SUBSCRIPTION, null, null, BillingPeriod.MONTHLY, false))).isFalse();
        assertThat(valid(plan(PaymentType.SUBSCRIPTION, 2_900, null, null, false))).isFalse();
        assertThat(valid(plan(PaymentType.SUBSCRIPTION, 0, null, BillingPeriod.MONTHLY, false))).isFalse();
    }

    @Test
    void payAsYouGoTakesOptionalTokenPrices() {
        assertThat(valid(plan(PaymentType.PAY_AS_YOU_GO, null, null, null, false))).isTrue();
        assertThat(valid(plan(PaymentType.PAY_AS_YOU_GO, null, new BigDecimal("0.0750"), null, false))).isTrue();
        assertThat(valid(plan(PaymentType.PAY_AS_YOU_GO, 100, null, null, false))).isFalse();
        assertThat(valid(plan(PaymentType.PAY_AS_YOU_GO, null, new BigDecimal("0.00001"), null, false))).isFalse();
    }

    @Test
    void aOneTimePaymentNeedsAPriceAndNoPeriod() {
        assertThat(valid(plan(PaymentType.ONE_TIME, 29_900, null, null, false))).isTrue();
        assertThat(valid(plan(PaymentType.ONE_TIME, null, null, null, false))).isFalse();
        assertThat(valid(plan(PaymentType.ONE_TIME, 29_900, null, BillingPeriod.MONTHLY, false))).isFalse();
    }

    @Test
    void contactSalesHasNoPriceButMayOfferATrial() {
        assertThat(valid(plan(PaymentType.CONTACT_SALES, null, null, null, true))).isTrue();
        assertThat(valid(plan(PaymentType.CONTACT_SALES, 100, null, null, false))).isFalse();
    }

    /** The directory's free trial filter matches on these two properties of the stored jsonb. */
    @Test
    void storesThePropertiesTheFreeTrialFilterMatchesOn() {
        JsonNode stored = new ObjectMapper().valueToTree(
                plan(PaymentType.SUBSCRIPTION, 2_900, null, BillingPeriod.MONTHLY, true));

        assertThat(stored.get("paymentType").asText()).isEqualTo("subscription");
        assertThat(stored.get("freeTrial").asBoolean()).isTrue();
        assertThat(stored.has("priceShapeValid")).isFalse();
    }

    @Test
    void aProductHasAtMostOneFreePlan() {
        ToolPlan free = plan(PaymentType.FREE, null, null, null, false);
        ToolPlan paid = plan(PaymentType.ONE_TIME, 100, null, null, false);

        assertThat(ToolPlan.hasAtMostOneFree(List.of(free, paid))).isTrue();
        assertThat(ToolPlan.hasAtMostOneFree(List.of(free, free))).isFalse();
    }
}
