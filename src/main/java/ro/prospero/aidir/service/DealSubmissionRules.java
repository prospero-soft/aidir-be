package ro.prospero.aidir.service;

import ro.prospero.aidir.data.DealDTO;
import ro.prospero.aidir.data.DealPayload;
import ro.prospero.aidir.data.DealPayload.Info;
import ro.prospero.aidir.data.DealPayload.Offer;
import ro.prospero.aidir.data.DealPayload.PlacementContent;
import ro.prospero.aidir.data.DealPayload.Schedule;
import ro.prospero.aidir.data.Placement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Whether a deal is complete enough to go to review. A draft can be saved with anything missing; these are
 * the fields the creation form marks required, checked once, when the vendor submits.
 *
 * <p>Each violation reads {@code <placement>.<field>: <message>}, or {@code <field>: <message>} for the deal
 * as a whole, and the message is written for the vendor: the form shows it under the Submit button with
 * the placement's tab label in front of it.
 */
public final class DealSubmissionRules {
    /** The options of the form's duration pickers - SCHEDULE_DURATIONS and friends in dealCreationData.ts. */
    static final Set<Integer> SCHEDULE_DAYS = Set.of(7, 14, 30, 60, 90);
    static final Set<Integer> DISCOUNT_MONTHS = Set.of(1, 3, 6, 12, 24);
    static final Set<Integer> TRIAL_DAYS = Set.of(7, 14, 30, 60);

    private DealSubmissionRules() {
    }

    /** The deal's product content, which every placement shows. */
    public record Basics(String productName, String category, String description, String logoUrl,
                         String longDescription) {
        public static Basics of(DealDTO deal) {
            return new Basics(deal.productName(), deal.category(), deal.description(), deal.logoUrl(),
                              deal.longDescription());
        }
    }

    public static List<String> check(List<Placement> placements,
                                     Basics basics,
                                     Map<String, PlacementContent> perPlacement,
                                     LocalDate today) {
        List<String> violations = new ArrayList<>();
        if (placements.isEmpty()) {
            violations.add("placements: Choose at least one placement");
        }
        checkBasics(new Fields(null, violations), basics);

        for (Placement placement : placements) {
            if (!placement.hasContent()) {
                continue;
            }
            Fields fields = new Fields(placement.wire(), violations);
            PlacementContent content = perPlacement.get(placement.wire());
            if (content == null) {
                fields.violation("content", "Fill in the schedule, details and offer");
                continue;
            }
            checkSchedule(fields, content.schedule(), today);
            checkInfo(fields, placement, content.info());
            checkOffer(fields, placement, content.offer());
        }
        return violations;
    }

    private static void checkSchedule(Fields fields, Schedule schedule, LocalDate today) {
        fields.require(schedule.durationDays() != null && SCHEDULE_DAYS.contains(schedule.durationDays()),
                       "durationDays", "Choose a duration");
        if (schedule.startDate() == null) {
            fields.violation("startDate", "Choose a start date");
        } else if (schedule.startDate().isBefore(today)) {
            fields.violation("startDate", DealPayload.PAST_START);
        }
    }

    private static void checkBasics(Fields fields, Basics basics) {
        fields.requireText(basics.productName(), "productName", "Product Name is required");
        fields.require(basics.category() != null, "category", "Choose a category");
        fields.requireText(basics.description(), "description", "Short description is required");
        fields.require(basics.logoUrl() != null, "logo", "Add a product logo or image");
        fields.requireText(basics.longDescription(), "longDescription", "Long Description is required");
    }

    private static void checkInfo(Fields fields, Placement placement, Info info) {
        if (placement == Placement.BANNER) {
            fields.requireText(info.line1(), "line1", "Line 1 is required");
            fields.requireText(info.line2(), "line2", "Line 2 is required");
        }
    }

    private static void checkOffer(Fields fields, Placement placement, Offer offer) {
        fields.requireText(offer.destinationUrl(), "destinationUrl", "Destination URL is required");

        if (placement == Placement.SPOTLIGHTS) {
            fields.requireText(offer.spotlightText(), "spotlightText", "Spotlight Text is required");
            return;
        }
        if (placement == Placement.BANNER) {
            fields.requireText(offer.bannerBadge(), "bannerBadge", "Banner Badge is required");
            return;
        }

        fields.requireText(offer.buttonText(), "buttonText", "Choose a button text");
        if (offer.offerType() == null) {
            fields.violation("offerType", "Choose an offer type");
            return;
        }
        switch (offer.offerType()) {
            case PERCENTAGE -> {
                checkDiscount(fields, offer, "Deal Price");
                fields.require(offer.discountMonths() != null && DISCOUNT_MONTHS.contains(offer.discountMonths()),
                               "discountMonths", "Choose a discount duration");
            }
            case FIXED -> checkDiscount(fields, offer, "Deal Price");
            case LIFETIME -> checkDiscount(fields, offer, "Lifetime Price");
            case TRIAL -> {
                fields.require(offer.trialDays() != null && TRIAL_DAYS.contains(offer.trialDays()),
                               "trialDays", "Choose a trial duration");
                fields.require(offer.currency() != null, "currency", "Choose a currency");
                fields.require(positive(offer.dealPrice()), "dealPrice", "Regular Price After Trial is required");
                fields.require(offer.billingPeriod() != null, "billingPeriod", "Choose a billing period");
            }
            case CREDITS -> {
                fields.require(offer.creditType() != null, "creditType", "Choose a credit type");
                fields.require(positive(offer.creditAmount()), "creditAmount", "Credit Amount is required");
                fields.require(offer.currency() != null, "currency", "Choose a currency");
                fields.require(positive(offer.estimatedValue()), "estimatedValue", "Estimated Value is required");
            }
            case CUSTOM -> fields.requireText(offer.customText(), "customText", "Custom Offer Text is required");
        }
    }

    /** The price types: an original price, and a lower one the deal is offered at. */
    private static void checkDiscount(Fields fields, Offer offer, String dealPriceLabel) {
        fields.require(offer.currency() != null, "currency", "Choose a currency");
        fields.require(positive(offer.originalPrice()), "originalPrice", "Original Price is required");
        if (offer.dealPrice() == null) {
            fields.violation("dealPrice", dealPriceLabel + " is required");
        } else if (offer.originalPrice() != null && offer.dealPrice().compareTo(offer.originalPrice()) >= 0) {
            fields.violation("dealPrice", dealPriceLabel + " must be lower than the Original Price");
        }
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    /** {@code placement} is null for the fields of the deal as a whole. */
    private record Fields(String placement, List<String> violations) {
        void violation(String field, String message) {
            violations.add((placement == null ? "" : placement + ".") + field + ": " + message);
        }

        void require(boolean satisfied, String field, String message) {
            if (!satisfied) {
                violation(field, message);
            }
        }

        void requireText(String value, String field, String message) {
            require(value != null && !value.isBlank(), field, message);
        }
    }
}
