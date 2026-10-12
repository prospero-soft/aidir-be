package ro.prospero.aidir.service;

import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import ro.prospero.aidir.data.DealPayload.PlacementContent;
import ro.prospero.aidir.data.DealStatus;
import ro.prospero.aidir.data.Placement;
import ro.prospero.aidir.data.PublicDealDTO;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static ro.prospero.aidir.jooq.generated.public_.Tables.DEAL;
import static ro.prospero.aidir.jooq.generated.public_.Tables.DEAL_PLACEMENT;

/**
 * The deals the public directory lists: approved, with a deal-card placement - Top Category or Homepage -
 * whose run includes today. The other placements have no public surface yet.
 */
@Service
public class PublicDealService {
    /** In order of preference: a deal running in both shows its Top Category content. */
    private static final List<Placement> LISTED = List.of(Placement.TOP_CATEGORY, Placement.HOMEPAGE);
    private static final String FREE_PLAN = "free";

    private final DSLContext dslContext;
    private final DealRows dealRows;
    private final SubscriptionService subscriptionService;

    public PublicDealService(DSLContext dslContext, DealRows dealRows, SubscriptionService subscriptionService) {
        this.dslContext = dslContext;
        this.dealRows = dealRows;
        this.subscriptionService = subscriptionService;
    }

    // todo: page this once there are more running deals than one response should carry; the directory
    //  filters and pages on the client for now
    public List<PublicDealDTO> listRunning() {
        LocalDate today = LocalDate.now();
        var running = dslContext.select(DEAL_PLACEMENT.DEAL_ID)
                                .from(DEAL_PLACEMENT)
                                .where(DEAL_PLACEMENT.PLACEMENT.in(LISTED.stream().map(Placement::wire).toList()))
                                .and(DEAL_PLACEMENT.START_DATE.le(today))
                                .and(DEAL_PLACEMENT.END_DATE.ge(today));
        List<DealRows.StoredDeal> deals = dealRows.read(dslContext,
                                                        DEAL.STATUS.eq(DealStatus.APPROVED.wire())
                                                                   .and(DEAL.ID.in(running)),
                                                        DEAL.REVIEWED_AT.desc());

        Map<Long, String> plans = subscriptionService.currentPlanNames(
                Set.copyOf(deals.stream().map(DealRows.StoredDeal::accountId).toList()));

        List<PublicDealDTO> cards = new ArrayList<>();
        for (DealRows.StoredDeal stored : deals) {
            String plan = plans.get(stored.accountId());
            boolean verified = plan != null && !FREE_PLAN.equals(plan);
            listedContent(stored.deal().perPlacement(), today)
                    .ifPresent(listed -> cards.add(toCard(stored.deal().id(), listed, verified)));
        }
        return cards;
    }

    private static Optional<Listed> listedContent(Map<String, PlacementContent> perPlacement, LocalDate today) {
        for (Placement placement : LISTED) {
            PlacementContent content = perPlacement.get(placement.wire());
            if (content != null && runsOn(content, today)) {
                return Optional.of(new Listed(placement, content));
            }
        }
        return Optional.empty();
    }

    /** The same inclusive end as the deal_placement.end_date column. */
    private static boolean runsOn(PlacementContent content, LocalDate day) {
        LocalDate start = content.schedule().startDate();
        Integer days = content.schedule().durationDays();
        return start != null && days != null && !day.isBefore(start) && !day.isAfter(start.plusDays(days - 1));
    }

    private static PublicDealDTO toCard(long dealId, Listed listed, boolean verified) {
        PlacementContent content = listed.content();
        LocalDate start = content.schedule().startDate();
        return new PublicDealDTO(dealId,
                                 listed.placement(),
                                 content.info().productName(),
                                 content.info().brand(),
                                 content.info().description(),
                                 content.info().category(),
                                 content.info().logoUrl(),
                                 content.offer(),
                                 start,
                                 start.plusDays(content.schedule().durationDays() - 1),
                                 verified);
    }

    private record Listed(Placement placement, PlacementContent content) {
    }
}
