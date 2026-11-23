package ro.prospero.aidir.service;

import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import ro.prospero.aidir.data.DealDTO;
import ro.prospero.aidir.data.DealPayload.PlacementContent;
import ro.prospero.aidir.data.DealStatus;
import ro.prospero.aidir.data.JsonbArrays;
import ro.prospero.aidir.data.Placement;
import ro.prospero.aidir.data.PublicDealDTO;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static ro.prospero.aidir.jooq.generated.Tables.DEAL;
import static ro.prospero.aidir.jooq.generated.Tables.DEAL_PLACEMENT;
import static ro.prospero.aidir.jooq.generated.Tables.TOOL;

/**
 * The deals the public directory lists: approved, from a vendor with a published tool, with a placement whose
 * run includes today.
 */
@Service
public class PublicDealService {
    /** In order of preference: a deal running in several shows the content of the first of them. */
    private static final List<Placement> LISTED = List.of(Placement.TOP_CATEGORY, Placement.HOMEPAGE,
                                                          Placement.SPOTLIGHTS, Placement.BANNER,
                                                          Placement.SIDEBAR, Placement.STANDARD_LISTING);
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
                                .where(DEAL_PLACEMENT.START_DATE.le(today))
                                .and(DEAL_PLACEMENT.END_DATE.ge(today));
        List<DealRows.StoredDeal> deals = dealRows.read(dslContext,
                                                        DEAL.STATUS.eq(DealStatus.APPROVED.wire())
                                                                   .and(DEAL.ID.in(running)),
                                                        DEAL.REVIEWED_AT.desc());

        Set<Long> accounts = Set.copyOf(deals.stream().map(DealRows.StoredDeal::accountId).toList());
        Map<Long, String> plans = subscriptionService.currentPlanNames(accounts);
        Map<Long, ToolAttributes> tools = publishedTools(accounts);

        List<PublicDealDTO> cards = new ArrayList<>();
        for (DealRows.StoredDeal stored : deals) {
            ToolAttributes tool = tools.get(stored.accountId());
            if (tool == null) {
                continue;
            }
            String plan = plans.get(stored.accountId());
            boolean verified = plan != null && !FREE_PLAN.equals(plan);
            listedContent(stored.deal().perPlacement(), today)
                    .ifPresent(listed -> cards.add(toCard(stored.deal(), listed, verified, tool)));
        }
        return cards;
    }

    /** A vendor has one tool for now; should there be several, the newest answers, as on the vendor's dashboard. */
    private Map<Long, ToolAttributes> publishedTools(Set<Long> accounts) {
        Map<Long, ToolAttributes> tools = new HashMap<>();
        dslContext.select(TOOL.ACCOUNT_ID, TOOL.PRICING_MODELS, TOOL.TARGET_USERS, TOOL.INTEGRATIONS)
                  .from(TOOL)
                  .where(TOOL.ACCOUNT_ID.in(accounts))
                  .and(TOOL.PUBLISHED.isTrue())
                  .orderBy(TOOL.APPROVED_AT.desc(), TOOL.ID.desc())
                  .forEach(row -> tools.putIfAbsent(
                          row.get(TOOL.ACCOUNT_ID),
                          new ToolAttributes(paymentTypes(JsonbArrays.readStringArray(row.get(TOOL.PRICING_MODELS))),
                                             JsonbArrays.readStringArray(row.get(TOOL.TARGET_USERS)),
                                             JsonbArrays.readStringArray(row.get(TOOL.INTEGRATIONS)))));
        return tools;
    }

    /** The stored models also mark a tool offering a trial, which is not a way of paying for it. */
    private static List<String> paymentTypes(List<String> models) {
        return models.stream().filter(model -> !ToolPricing.FREE_TRIAL.equals(model)).toList();
    }

    static Optional<Listed> listedContent(Map<String, PlacementContent> perPlacement, LocalDate today) {
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

    private static PublicDealDTO toCard(DealDTO deal, Listed listed, boolean verified, ToolAttributes tool) {
        PlacementContent content = listed.content();
        LocalDate start = content.schedule().startDate();
        return new PublicDealDTO(deal.id(),
                                 listed.placement(),
                                 deal.productName(),
                                 deal.brand(),
                                 deal.description(),
                                 deal.category(),
                                 deal.logoUrl(),
                                 content.offer(),
                                 start,
                                 start.plusDays(content.schedule().durationDays() - 1),
                                 verified,
                                 tool.pricingModels(),
                                 tool.targetUsers(),
                                 tool.integrations());
    }

    record Listed(Placement placement, PlacementContent content) {
    }

    private record ToolAttributes(List<String> pricingModels, List<String> targetUsers, List<String> integrations) {
    }
}
