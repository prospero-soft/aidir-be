package ro.prospero.aidir.service;

import org.junit.jupiter.api.Test;
import ro.prospero.aidir.data.DealPayload.Info;
import ro.prospero.aidir.data.DealPayload.Offer;
import ro.prospero.aidir.data.DealPayload.PlacementContent;
import ro.prospero.aidir.data.DealPayload.Schedule;
import ro.prospero.aidir.data.OfferType;
import ro.prospero.aidir.data.Placement;
import ro.prospero.aidir.service.DealSubmissionRules.Basics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DealSubmissionRulesTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);
    private static final Schedule SCHEDULE = new Schedule(30, TODAY);
    private static final Basics BASICS = new Basics("Acme Writer", "Content Creation", "Write faster",
                                                   "/media/deals/1/logo-abc.png", "Writes everything faster.");
    private static final Info NO_INFO = new Info(null, null);

    private static Offer offer(OfferType type) {
        return new Offer(type, "EUR", new BigDecimal("100"), new BigDecimal("60"), 12, null, null, null, null,
                         null, null, null, "Get Started", "https://acme.example", null, null);
    }

    private static List<String> check(Placement placement, PlacementContent content) {
        return DealSubmissionRules.check(List.of(placement), BASICS, Map.of(placement.wire(), content), TODAY);
    }

    @Test
    void aCompleteListingPasses() {
        assertThat(check(Placement.TOP_CATEGORY,
                         new PlacementContent(SCHEDULE, NO_INFO, offer(OfferType.PERCENTAGE)))).isEmpty();
    }

    @Test
    void noPlacementsIsAViolation() {
        assertThat(DealSubmissionRules.check(List.of(), BASICS, Map.of(), TODAY))
                .containsExactly("placements: Choose at least one placement");
    }

    @Test
    void placementsWithoutContentNeedNothingElse() {
        assertThat(DealSubmissionRules.check(List.of(Placement.SIDEBAR, Placement.STANDARD_LISTING), BASICS,
                                             Map.of(), TODAY))
                .isEmpty();
    }

    @Test
    void aChosenContentPlacementWithNoContentIsAViolation() {
        assertThat(DealSubmissionRules.check(List.of(Placement.BANNER), BASICS, Map.of(), TODAY))
                .containsExactly("banner.content: Fill in the schedule, details and offer");
    }

    @Test
    void theDealNeedsItsProductBasicsWhateverItsPlacements() {
        Basics empty = new Basics(" ", null, null, null, " ");

        assertThat(DealSubmissionRules.check(List.of(Placement.SIDEBAR), empty, Map.of(), TODAY))
                .containsExactly("productName: Product Name is required",
                                 "category: Choose a category",
                                 "description: Short description is required",
                                 "logo: Add a product logo or image",
                                 "longDescription: Long Description is required");
    }

    @Test
    void theScheduleNeedsAKnownDurationAndAStartThatIsNotPast() {
        PlacementContent content = new PlacementContent(new Schedule(10, TODAY.minusDays(1)), NO_INFO,
                                                        offer(OfferType.FIXED));

        assertThat(check(Placement.HOMEPAGE, content))
                .containsExactly("homepage.durationDays: Choose a duration",
                                 "homepage.startDate: The start date can't be in the past");
    }

    @Test
    void theDealPriceMustBeLowerThanTheOriginal() {
        Offer offer = new Offer(OfferType.LIFETIME, "EUR", new BigDecimal("50"), new BigDecimal("50"), null, null,
                                null, null, null, null, null, null, "Get Deal", "https://acme.example", null, null);

        assertThat(check(Placement.TOP_CATEGORY, new PlacementContent(SCHEDULE, NO_INFO, offer)))
                .containsExactly("top-category.dealPrice: Lifetime Price must be lower than the Original Price");
    }

    @Test
    void eachOfferTypeAsksForItsOwnFields() {
        Offer trial = new Offer(OfferType.TRIAL, "EUR", null, null, null, 45, null, null, null, null, null, null,
                                "Subscribe", "https://acme.example", null, null);
        Offer credits = new Offer(OfferType.CREDITS, "USD", null, null, null, null, null, "Tokens",
                                  new BigDecimal("1000"), null, null, null, "Get Deal", "https://acme.example",
                                  null, null);
        Offer custom = new Offer(OfferType.CUSTOM, null, null, null, null, null, null, null, null, null, "",
                                 null, "Get Deal", "https://acme.example", null, null);

        assertThat(check(Placement.HOMEPAGE, new PlacementContent(SCHEDULE, NO_INFO, trial)))
                .containsExactly("homepage.trialDays: Choose a trial duration",
                                 "homepage.dealPrice: Regular Price After Trial is required",
                                 "homepage.billingPeriod: Choose a billing period");
        assertThat(check(Placement.HOMEPAGE, new PlacementContent(SCHEDULE, NO_INFO, credits)))
                .containsExactly("homepage.estimatedValue: Estimated Value is required");
        assertThat(check(Placement.HOMEPAGE, new PlacementContent(SCHEDULE, NO_INFO, custom)))
                .containsExactly("homepage.customText: Custom Offer Text is required");
    }

    @Test
    void aSpotlightNeedsItsTextAndADestination() {
        Offer offer = new Offer(null, null, null, null, null, null, null, null, null, null, null, null, null,
                                null, " ", null);

        assertThat(check(Placement.SPOTLIGHTS, new PlacementContent(SCHEDULE, NO_INFO, offer)))
                .containsExactly("spotlights.destinationUrl: Destination URL is required",
                                 "spotlights.spotlightText: Spotlight Text is required");
    }

    @Test
    void aBannerNeedsBothLinesAndABadge() {
        Info info = new Info("Flash Sale", null);
        Offer offer = new Offer(null, null, null, null, null, null, null, null, null, null, null, null, null,
                                "https://acme.example", null, " ");

        assertThat(check(Placement.BANNER, new PlacementContent(SCHEDULE, info, offer)))
                .containsExactly("banner.line2: Line 2 is required",
                                 "banner.bannerBadge: Banner Badge is required");
    }
}
