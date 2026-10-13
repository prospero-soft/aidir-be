package ro.prospero.aidir.service;

import org.junit.jupiter.api.Test;
import ro.prospero.aidir.data.DealPayload.Info;
import ro.prospero.aidir.data.DealPayload.Offer;
import ro.prospero.aidir.data.DealPayload.PlacementContent;
import ro.prospero.aidir.data.DealPayload.Schedule;
import ro.prospero.aidir.data.OfferType;
import ro.prospero.aidir.data.Placement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DealSubmissionRulesTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);
    private static final Schedule SCHEDULE = new Schedule(30, TODAY);
    private static final String LOGO = "/media/deals/1/top-category-logo-abc.png";
    private static final String LONG_DESCRIPTION = "Writes everything faster.";

    private static Info listingInfo() {
        return new Info("Acme Writer", "Content Creation", null, "Write faster", LOGO, null, null);
    }

    private static Offer offer(OfferType type) {
        return new Offer(type, "EUR", new BigDecimal("100"), new BigDecimal("60"), 12, null, null, null, null,
                         null, null, null, "Get Started", "https://acme.example", null, null);
    }

    private static List<String> check(Placement placement, PlacementContent content) {
        return DealSubmissionRules.check(List.of(placement), LONG_DESCRIPTION, Map.of(placement.wire(), content),
                                         TODAY);
    }

    @Test
    void aCompleteListingPasses() {
        assertThat(check(Placement.TOP_CATEGORY,
                         new PlacementContent(SCHEDULE, listingInfo(), offer(OfferType.PERCENTAGE)))).isEmpty();
    }

    @Test
    void noPlacementsIsAViolation() {
        assertThat(DealSubmissionRules.check(List.of(), LONG_DESCRIPTION, Map.of(), TODAY))
                .containsExactly("placements: Choose at least one placement");
    }

    @Test
    void placementsWithoutContentNeedNothingElse() {
        assertThat(DealSubmissionRules.check(List.of(Placement.SIDEBAR, Placement.STANDARD_LISTING), LONG_DESCRIPTION,
                                             Map.of(), TODAY))
                .isEmpty();
    }

    @Test
    void aChosenContentPlacementWithNoContentIsAViolation() {
        assertThat(DealSubmissionRules.check(List.of(Placement.BANNER), LONG_DESCRIPTION, Map.of(), TODAY))
                .containsExactly("banner.content: Fill in the schedule, details and offer");
    }

    @Test
    void theDealNeedsALongDescriptionWhateverItsPlacements() {
        assertThat(DealSubmissionRules.check(List.of(Placement.SIDEBAR), " ", Map.of(), TODAY))
                .containsExactly("longDescription: Long Description is required");
    }

    @Test
    void theScheduleNeedsAKnownDurationAndAStartThatIsNotPast() {
        PlacementContent content = new PlacementContent(new Schedule(10, TODAY.minusDays(1)), listingInfo(),
                                                        offer(OfferType.FIXED));

        assertThat(check(Placement.HOMEPAGE, content))
                .containsExactly("homepage.durationDays: Choose a duration",
                                 "homepage.startDate: The start date can't be in the past");
    }

    @Test
    void aListingNeedsItsDetailsAndALogo() {
        Info empty = new Info(" ", null, null, null, null, null, null);

        assertThat(check(Placement.TOP_CATEGORY, new PlacementContent(SCHEDULE, empty, offer(OfferType.FIXED))))
                .containsExactly("top-category.productName: Product Name is required",
                                 "top-category.category: Choose a category",
                                 "top-category.description: Short description is required",
                                 "top-category.logo: Add a product logo or image");
    }

    @Test
    void theDealPriceMustBeLowerThanTheOriginal() {
        Offer offer = new Offer(OfferType.LIFETIME, "EUR", new BigDecimal("50"), new BigDecimal("50"), null, null,
                                null, null, null, null, null, null, "Get Deal", "https://acme.example", null, null);

        assertThat(check(Placement.TOP_CATEGORY, new PlacementContent(SCHEDULE, listingInfo(), offer)))
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

        assertThat(check(Placement.HOMEPAGE, new PlacementContent(SCHEDULE, listingInfo(), trial)))
                .containsExactly("homepage.trialDays: Choose a trial duration",
                                 "homepage.dealPrice: Regular Price After Trial is required",
                                 "homepage.billingPeriod: Choose a billing period");
        assertThat(check(Placement.HOMEPAGE, new PlacementContent(SCHEDULE, listingInfo(), credits)))
                .containsExactly("homepage.estimatedValue: Estimated Value is required");
        assertThat(check(Placement.HOMEPAGE, new PlacementContent(SCHEDULE, listingInfo(), custom)))
                .containsExactly("homepage.customText: Custom Offer Text is required");
    }

    @Test
    void aSpotlightNeedsANameALogoAndItsText() {
        Info info = new Info("Acme Writer", null, null, null, null, null, null);
        Offer offer = new Offer(null, null, null, null, null, null, null, null, null, null, null, null, null,
                                null, "60% OFF", null);

        assertThat(check(Placement.SPOTLIGHTS, new PlacementContent(SCHEDULE, info, offer)))
                .containsExactly("spotlights.logo: Add a product logo or image",
                                 "spotlights.destinationUrl: Destination URL is required");
    }

    @Test
    void aBannerNeedsBothLinesAndABadge() {
        Info info = new Info(null, null, null, null, null, "Flash Sale", null);
        Offer offer = new Offer(null, null, null, null, null, null, null, null, null, null, null, null, null,
                                "https://acme.example", null, " ");

        assertThat(check(Placement.BANNER, new PlacementContent(SCHEDULE, info, offer)))
                .containsExactly("banner.line2: Line 2 is required",
                                 "banner.bannerBadge: Banner Badge is required");
    }
}
