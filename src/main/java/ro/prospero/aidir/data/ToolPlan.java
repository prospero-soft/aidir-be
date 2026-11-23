package ro.prospero.aidir.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * One pricing plan, as the onboarding wizard submits it, the dashboard edits it and the {@code plans} jsonb
 * holds it. One record for all three is what keeps them enforcing the same rules.
 *
 * <p>Which price fields apply depends on the payment type: a subscription has a price and a billing period, a
 * one-time payment only a price, and pay-as-you-go optional prices per million tokens, left blank by tools
 * that bill per image or minute instead. Free and Contact Sales have no price at all.
 */
public record ToolPlan(
        @NotBlank String id,
        @NotBlank String name,
        @NotNull PaymentType paymentType,
        @Positive @Max(MAX_PRICE_CENTS) Integer priceCents,
        @DecimalMin("0.0001") @Digits(integer = 6, fraction = 4) BigDecimal inputPerMillionEur,
        @DecimalMin("0.0001") @Digits(integer = 6, fraction = 4) BigDecimal outputPerMillionEur,
        BillingPeriod billingPeriod,
        boolean freeTrial,
        @NotNull @Size(min = 1, max = MAX_FEATURES) List<@NotBlank @Size(max = FEATURE_MAX_LENGTH) String> features
) {
    public static final int MAX_FEATURES = 4;
    public static final int FEATURE_MAX_LENGTH = 80;
    public static final int MAX_PRICE_CENTS = 100_000_000;

    public enum PaymentType {
        FREE("free"),
        SUBSCRIPTION("subscription"),
        PAY_AS_YOU_GO("pay_as_you_go"),
        ONE_TIME("one_time"),
        CONTACT_SALES("contact_sales");

        private final String wire;

        PaymentType(String wire) {
            this.wire = wire;
        }

        @JsonValue
        public String wire() {
            return wire;
        }

        @JsonCreator
        public static PaymentType fromWire(String value) {
            for (PaymentType type : values()) {
                if (type.wire.equals(value)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown payment type: " + value);
        }
    }

    public enum BillingPeriod {
        MONTHLY("monthly"),
        ANNUAL("annual");

        private final String wire;

        BillingPeriod(String wire) {
            this.wire = wire;
        }

        @JsonValue
        public String wire() {
            return wire;
        }

        @JsonCreator
        public static BillingPeriod fromWire(String value) {
            for (BillingPeriod period : values()) {
                if (period.wire.equals(value)) {
                    return period;
                }
            }
            throw new IllegalArgumentException("Unknown billing period: " + value);
        }
    }

    @JsonIgnore
    @AssertTrue(message = "the price fields must match the plan's payment type")
    public boolean isPriceShapeValid() {
        if (paymentType == null) {
            return true;
        }
        boolean noTokenPrices = inputPerMillionEur == null && outputPerMillionEur == null;
        return switch (paymentType) {
            case FREE -> priceCents == null && billingPeriod == null && noTokenPrices && !freeTrial;
            case SUBSCRIPTION -> priceCents != null && billingPeriod != null && noTokenPrices;
            case PAY_AS_YOU_GO -> priceCents == null && billingPeriod == null;
            case ONE_TIME -> priceCents != null && billingPeriod == null && noTokenPrices;
            case CONTACT_SALES -> priceCents == null && billingPeriod == null && noTokenPrices;
        };
    }

    /** A product has at most one free plan. */
    public static boolean hasAtMostOneFree(List<ToolPlan> plans) {
        return plans == null
               || plans.stream().filter(plan -> plan != null && plan.paymentType() == PaymentType.FREE).count() <= 1;
    }
}
