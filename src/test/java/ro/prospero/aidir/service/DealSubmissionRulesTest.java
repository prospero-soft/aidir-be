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
    private static final Basics BASICS = new Basics("Acme Writer", "Writing & Content Creation", "Write faster",
                                                   "/media/deals/1/logo-abc.png", "Writes everything faster.");
    private static final Info NO_INFO = new Info(null, null);

    private static Offer offer(OfferType type) {
        return new Offer(type, "EUR", new BigDecimal("100"), new BigDecimal("60"), 40, 12, null, null, null, null,
                         null, null, null, "Get Started", "https://acme.example", null, null);
    }

    private static Offer percentage(Integer percent) {
        return new Offer(OfferType.PERCENTAGE, "EUR", new BigDecimal("100"), null, percent, 12, null, null, null,
                         null, null, null, null, "Get Started", "https://acme.example", null, null);
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
    void everyPlacementNeedsContent() {
        assertThat(DealSubmissionRules.check(List.of(Placement.SIDEBAR, Placement.STANDARD_LISTING), BASICS,
                                             Map.of(), TODAY))
                .containsExactly("sidebar.content: Fill in the schedule, details and offer",
                                 "standard-listing.content: Fill in the schedule, details and offer");
    }

    @Test
    void aSidebarOrStandardListingWithAnOfferPasses() {
        PlacementContent content = new PlacementContent(SCHEDULE, NO_INFO, offer(OfferType.FIXED));

        assertThat(check(Placement.SIDEBAR, content)).isEmpty();
        assertThat(check(Placement.STANDARD_LISTING, content)).isEmpty();
    }

    @Test
    void aChosenContentPlacementWithNoContentIsAViolation() {
        assertThat(DealSubmissionRules.check(List.of(Placement.BANNER), BASICS, Map.of(), TODAY))
                .containsExactly("banner.content: Fill in the schedule, details and offer");
    }

    @Test
    void theDealNeedsItsProductBasicsWhateverItsPlacements() {
        Basics empty = new Basics(" ", null, null, null, " ");
        PlacementContent content = new PlacementContent(SCHEDULE, NO_INFO, offer(OfferType.FIXED));

        assertThat(DealSubmissionRules.check(List.of(Placement.SIDEBAR), empty,
                                             Map.of(Placement.SIDEBAR.wire(), content), TODAY))
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
                                null, null, null, null, null, null, null, "Get Deal", "https://acme.example",
                                null, null);

        assertThat(check(Placement.TOP_CATEGORY, new PlacementContent(SCHEDULE, NO_INFO, offer)))
                .containsExactly("top-category.dealPrice: Lifetime Price must be lower than the Original Price");
    }

    @Test
    void aPercentageOfferTakesADiscountRatherThanADealPrice() {
        assertThat(check(Placement.HOMEPAGE, new PlacementContent(SCHEDULE, NO_INFO, percentage(1)))).isEmpty();
        assertThat(check(Placement.HOMEPAGE, new PlacementContent(SCHEDULE, NO_INFO, percentage(99)))).isEmpty();

        for (Integer outOfRange : new Integer[]{null, 0, 100}) {
            assertThat(check(Placement.HOMEPAGE, new PlacementContent(SCHEDULE, NO_INFO, percentage(outOfRange))))
                    .containsExactly("homepage.discountPercent: Enter a discount between 1% and 99%");
        }
    }

    @Test
    void freeMonthsNeedAMonthlyPriceAndANumberOfMonths() {
        Offer complete = new Offer(OfferType.FREE_MONTHS, "EUR", new BigDecimal("20"), null, null, 3, null, null,
                                   null, null, null, null, null, "Get Deal", "https://acme.example", null, null);
        Offer empty = new Offer(OfferType.FREE_MONTHS, "EUR", null, null, null, null, null, null, null, null, null,
                                null, null, "Get Deal", "https://acme.example", null, null);

        assertThat(check(Placement.HOMEPAGE, new PlacementContent(SCHEDULE, NO_INFO, complete))).isEmpty();
        assertThat(check(Placement.HOMEPAGE, new PlacementContent(SCHEDULE, NO_INFO, empty)))
                .containsExactly("homepage.originalPrice: Monthly Price is required",
                                 "homepage.discountMonths: Choose how many months are free");
    }

    @Test
    void bonusCreditsAskForTheSameFieldsAsFreeCredits() {
        Offer bonus = new Offer(OfferType.BONUS_CREDITS, "USD", null, null, null, null, null, null, "Tokens",
                                new BigDecimal("1000"), null, null, null, "Get Deal", "https://acme.example",
                                null, null);

        assertThat(check(Placement.HOMEPAGE, new PlacementContent(SCHEDULE, NO_INFO, bonus)))
                .containsExactly("homepage.estimatedValue: Estimated Value is required");
    }

    @Test
    void eachOfferTypeAsksForItsOwnFields() {
        Offer trial = new Offer(OfferType.TRIAL, "EUR", null, null, null, null, 45, null, null, null, null, null,
                                null, "Subscribe", "https://acme.example", null, null);
        Offer credits = new Offer(OfferType.CREDITS, "USD", null, null, null, null, null, null, "Tokens",
                                  new BigDecimal("1000"), null, null, null, "Get Deal", "https://acme.example",
                                  null, null);
        Offer custom = new Offer(OfferType.CUSTOM, null, null, null, null, null, null, null, null, null, null, "",
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
    void aSpotlightNeedsItsTextADestinationAndAnOffer() {
        Offer offer = new Offer(null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                                null, " ", null);

        assertThat(check(Placement.SPOTLIGHTS, new PlacementContent(SCHEDULE, NO_INFO, offer)))
                .containsExactly("spotlights.destinationUrl: Destination URL is required",
                                 "spotlights.spotlightText: Spotlight Text is required",
                                 "spotlights.buttonText: Choose a button text",
                                 "spotlights.offerType: Choose an offer type");
    }

    @Test
    void aBannerNeedsBothLinesABadgeAndAnOffer() {
        Info info = new Info("Flash Sale", null);
        Offer offer = new Offer(null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                                "https://acme.example", null, " ");

        assertThat(check(Placement.BANNER, new PlacementContent(SCHEDULE, info, offer)))
                .containsExactly("banner.line2: Line 2 is required",
                                 "banner.bannerBadge: Banner Badge is required",
                                 "banner.buttonText: Choose a button text",
                                 "banner.offerType: Choose an offer type");
    }
}
