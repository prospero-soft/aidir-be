package ro.prospero.aidir.data;

import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * What the deal creation form saves, in the shape the form holds it. Mirrors buildDealPayload in aidir-fe
 * (src/components/deal-creation/dealPayload.ts).
 *
 * <p>A draft may be saved half-filled, so the constraints here are only about form - lengths, formats,
 * the closed vocabularies, and a start date that is not in the past. Whether a deal is complete enough to review is decided on submit, by
 * {@link ro.prospero.aidir.service.DealSubmissionRules}.
 *
 * <p>{@code perPlacement} is keyed by {@link Placement#wire()} and only holds the placements with content.
 * Logos travel as multipart parts beside this payload, one {@code logo-<placement>} each.
 */
public record DealPayload(@NotNull List<Placement> placements,
                          boolean sponsored,
                          @Size(max = 60) String sponsoredLabel,
                          @Size(max = 100) String campaign,
                          @Size(max = 100) String internalCampaignId,
                          @NotNull Map<String, @Valid PlacementContent> perPlacement) {

    public static final String CURRENCIES = "EUR|USD|GBP";
    public static final String BILLING_PERIODS = "week|month|year";
    public static final String CREDIT_TYPES = "Tokens|Credits|API Calls|Minutes";
    public static final String DEAL_LABELS = "Limited Time|Best Seller|New|Exclusive|Members Only";
    public static final String BUTTON_TEXTS =
            "Subscribe|View Deal|Get Started|Subscribe Now|Get Deal|Get Deal for Free";
    public static final String HTTP_URL = "^https?://\\S+$";
    public static final String PAST_START = "The start date can't be in the past";

    public record PlacementContent(@NotNull @Valid Schedule schedule,
                                   @NotNull @Valid Info info,
                                   @NotNull @Valid Offer offer) {
    }

    /**
     * A start date in the past is refused even on a draft. A saved draft's date can still fall behind the
     * calendar; it is then refused on the next save and by {@link ro.prospero.aidir.service.DealSubmissionRules}
     * on submit, until the vendor picks a new one.
     */
    public record Schedule(@Min(1) @Max(366) Integer durationDays,
                           @FutureOrPresent(message = PAST_START) LocalDate startDate) {
    }

    /**
     * {@code logoUrl} is how a saved logo survives an edit: sent back as it was read, the stored logo is
     * kept; sent as null, it is removed. A new {@code logo-<placement>} part replaces it either way.
     */
    public record Info(@Size(max = 200) String productName,
                       String category,
                       @Size(max = 200) String brand,
                       @Size(max = 80) String description,
                       @Size(max = 1000) String longDescription,
                       String logoUrl,
                       @Size(max = 45) String line1,
                       @Size(max = 80) String line2) {
    }

    /** Flat, like the form: which of the type-specific fields matter depends on {@code offerType}. */
    public record Offer(OfferType offerType,
                        @Pattern(regexp = CURRENCIES) String currency,
                        @PositiveOrZero BigDecimal originalPrice,
                        @PositiveOrZero BigDecimal dealPrice,
                        @Min(1) @Max(120) Integer discountMonths,
                        @Min(1) @Max(366) Integer trialDays,
                        @Pattern(regexp = BILLING_PERIODS) String billingPeriod,
                        @Pattern(regexp = CREDIT_TYPES) String creditType,
                        @PositiveOrZero BigDecimal creditAmount,
                        @PositiveOrZero BigDecimal estimatedValue,
                        @Size(max = 45) String customText,
                        @Pattern(regexp = DEAL_LABELS) String dealLabel,
                        @Pattern(regexp = BUTTON_TEXTS) String buttonText,
                        @Size(max = 500) @Pattern(regexp = HTTP_URL) String destinationUrl,
                        @Size(max = 20) String spotlightText,
                        @Size(max = 15) String bannerBadge) {
    }
}
