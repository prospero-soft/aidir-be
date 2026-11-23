package ro.prospero.aidir.service;

import org.junit.jupiter.api.Test;
import ro.prospero.aidir.data.DealPayload.Info;
import ro.prospero.aidir.data.DealPayload.Offer;
import ro.prospero.aidir.data.DealPayload.PlacementContent;
import ro.prospero.aidir.data.DealPayload.Schedule;
import ro.prospero.aidir.data.Placement;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PublicDealServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);
    private static final Offer OFFER = new Offer(null, null, null, null, null, null, null, null, null, null, null,
                                                 null, null, null, null, null, null);

    private static PlacementContent runningFrom(LocalDate start) {
        return new PlacementContent(new Schedule(7, start), new Info(null, null), OFFER);
    }

    @Test
    void everyPlacementListsTheDealOnItsOwn() {
        for (Placement placement : Placement.values()) {
            var listed = PublicDealService.listedContent(Map.of(placement.wire(), runningFrom(TODAY)), TODAY);

            assertThat(listed).isPresent();
            assertThat(listed.get().placement()).isEqualTo(placement);
        }
    }

    @Test
    void aDealInSeveralPlacementsShowsTheMostPreferred() {
        List<Placement> preference = List.of(Placement.TOP_CATEGORY, Placement.HOMEPAGE, Placement.SPOTLIGHTS,
                                             Placement.BANNER, Placement.SIDEBAR, Placement.STANDARD_LISTING);
        Map<String, PlacementContent> perPlacement = new HashMap<>();
        Arrays.stream(Placement.values()).forEach(p -> perPlacement.put(p.wire(), runningFrom(TODAY)));

        for (Placement expected : preference) {
            assertThat(PublicDealService.listedContent(perPlacement, TODAY).orElseThrow().placement())
                    .isEqualTo(expected);
            perPlacement.remove(expected.wire());
        }
        assertThat(PublicDealService.listedContent(perPlacement, TODAY)).isEmpty();
    }

    @Test
    void aPreferredPlacementThatIsNotRunningGivesWayToOneThatIs() {
        var listed = PublicDealService.listedContent(
                Map.of(Placement.TOP_CATEGORY.wire(), runningFrom(TODAY.plusDays(1)),
                       Placement.STANDARD_LISTING.wire(), runningFrom(TODAY)),
                TODAY);

        assertThat(listed.orElseThrow().placement()).isEqualTo(Placement.STANDARD_LISTING);
    }

    @Test
    void aPlacementRunsThroughItsLastDayAndNoFurther() {
        LocalDate endsToday = TODAY.minusDays(6);
        LocalDate endedYesterday = TODAY.minusDays(7);

        assertThat(PublicDealService.listedContent(Map.of(Placement.SIDEBAR.wire(), runningFrom(endsToday)), TODAY))
                .isPresent();
        assertThat(PublicDealService.listedContent(Map.of(Placement.SIDEBAR.wire(), runningFrom(endedYesterday)),
                                                   TODAY))
                .isEmpty();
    }

    @Test
    void aPlacementADraftNeverFilledInIsNotListed() {
        PlacementContent blank = new PlacementContent(new Schedule(null, null), new Info(null, null), OFFER);

        assertThat(PublicDealService.listedContent(Map.of(Placement.BANNER.wire(), blank), TODAY)).isEmpty();
    }
}
