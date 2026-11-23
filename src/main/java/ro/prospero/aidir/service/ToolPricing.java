package ro.prospero.aidir.service;

import ro.prospero.aidir.data.ToolPlan;
import ro.prospero.aidir.data.ToolPlan.BillingPeriod;
import ro.prospero.aidir.data.ToolPlan.PaymentType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What the directory filters and sorts on, read off the plans the vendor described. The onboarding wizard and
 * the dashboard's pricing editor both describe plans, so both derive these here.
 *
 * <p>Price ranges are per month, so an annual subscription counts at a twelfth of its price. Pay-as-you-go and
 * one-time plans have no monthly cost and match no range. A tool matches every range one of its plans falls in.
 */
final class ToolPricing {
    static final String FREE_TRIAL = "free_trial";

    private static final String RANGE_FREE = "free";
    private static final String RANGE_UNDER_10 = "under_10";
    private static final String RANGE_10_50 = "10_50";
    private static final String RANGE_50_200 = "50_200";
    private static final String RANGE_200_PLUS = "200_plus";
    private static final String RANGE_CUSTOM = "custom";
    private static final List<String> RANGES = List.of(RANGE_FREE, RANGE_UNDER_10, RANGE_10_50, RANGE_50_200,
                                                       RANGE_200_PLUS, RANGE_CUSTOM);

    private static final int MONTHS_IN_YEAR = 12;

    record Derived(List<String> models, List<String> priceRanges, Integer lowestMonthlyCents) {
    }

    private ToolPricing() {
    }

    static Derived of(List<ToolPlan> plans) {
        List<ToolPlan> all = plans == null ? List.of() : plans;

        Set<PaymentType> types = all.stream().map(ToolPlan::paymentType).collect(Collectors.toSet());
        List<String> models = new ArrayList<>(Arrays.stream(PaymentType.values())
                                                    .filter(types::contains)
                                                    .map(PaymentType::wire)
                                                    .toList());
        if (all.stream().anyMatch(ToolPlan::freeTrial)) {
            models.add(FREE_TRIAL);
        }

        Set<String> matched = all.stream()
                                 .map(ToolPricing::rangeOf)
                                 .filter(Objects::nonNull)
                                 .collect(Collectors.toSet());
        List<String> ranges = RANGES.stream().filter(matched::contains).toList();

        Integer lowest = all.stream()
                            .map(ToolPricing::monthlyCents)
                            .filter(Objects::nonNull)
                            .min(Integer::compare)
                            .orElse(null);

        return new Derived(models, ranges, lowest);
    }

    private static String rangeOf(ToolPlan plan) {
        return switch (plan.paymentType()) {
            case FREE -> RANGE_FREE;
            case CONTACT_SALES -> RANGE_CUSTOM;
            case SUBSCRIPTION -> bracket(monthlyCents(plan));
            case PAY_AS_YOU_GO, ONE_TIME -> null;
        };
    }

    private static String bracket(int monthlyCents) {
        if (monthlyCents < 1_000) {
            return RANGE_UNDER_10;
        }
        if (monthlyCents < 5_000) {
            return RANGE_10_50;
        }
        return monthlyCents < 20_000 ? RANGE_50_200 : RANGE_200_PLUS;
    }

    /** Rounding down cannot move a price across a bracket, because every bound is a whole number of cents. */
    private static Integer monthlyCents(ToolPlan plan) {
        return switch (plan.paymentType()) {
            case FREE -> 0;
            case SUBSCRIPTION -> plan.billingPeriod() == BillingPeriod.ANNUAL
                                 ? plan.priceCents() / MONTHS_IN_YEAR
                                 : plan.priceCents();
            case PAY_AS_YOU_GO, ONE_TIME, CONTACT_SALES -> null;
        };
    }
}
