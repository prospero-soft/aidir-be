package ro.prospero.aidir.service;

import java.util.List;
import java.util.function.Function;

/**
 * The {@code pricing} reference the directory filters on, read off the plans the vendor described: nothing
 * paid is free, a free tier next to paid ones is freemium, anything else is paid. The onboarding wizard and
 * the dashboard's pricing editor both describe plans, so both derive the reference here.
 */
final class ToolPricing {

    private ToolPricing() {
    }

    /**
     * @param planId    the plan's id, compared against {@code freeTierPlanId}
     * @param planPrice the plan's price as typed
     */
    static <P> String of(List<P> plans,
                         String freeTierPlanId,
                         Function<P, String> planId,
                         Function<P, String> planPrice) {
        List<P> all = plans == null ? List.of() : plans;
        boolean anyPaid = all.stream().anyMatch(plan -> !isFree(plan, freeTierPlanId, planId, planPrice));
        if (!anyPaid) {
            return "free";
        }
        boolean anyFree = all.stream().anyMatch(plan -> isFree(plan, freeTierPlanId, planId, planPrice));
        return anyFree ? "freemium" : "paid";
    }

    /**
     * The form marks one plan as the free tier and forces its price to "0", but the price is free text
     * otherwise - "€0", "0.00" and an empty field all mean the same thing and none of them is a number.
     */
    private static <P> boolean isFree(P plan,
                                      String freeTierPlanId,
                                      Function<P, String> planId,
                                      Function<P, String> planPrice) {
        String id = planId.apply(plan);
        if (id != null && id.equals(freeTierPlanId)) {
            return true;
        }
        String price = planPrice.apply(plan) == null ? "" : planPrice.apply(plan).trim();
        if (price.isEmpty() || "free".equalsIgnoreCase(price)) {
            return true;
        }
        String digits = price.replaceAll("[^0-9]", "");
        return !digits.isEmpty() && digits.chars().allMatch(digit -> digit == '0');
    }
}
