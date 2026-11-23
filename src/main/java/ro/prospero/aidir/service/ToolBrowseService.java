package ro.prospero.aidir.service;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ro.prospero.aidir.data.CategoryCount;
import ro.prospero.aidir.data.FacetCount;
import ro.prospero.aidir.data.ToolBrowseQuery;
import ro.prospero.aidir.data.ToolFilter;
import ro.prospero.aidir.data.ToolPage;
import ro.prospero.aidir.data.ToolPlan.PaymentType;
import ro.prospero.aidir.data.ToolSearchHit;
import ro.prospero.aidir.jooq.generated.tables.records.ToolRecord;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static ro.prospero.aidir.jooq.generated.Tables.CATEGORY;
import static ro.prospero.aidir.jooq.generated.Tables.PRICING;
import static ro.prospero.aidir.jooq.generated.Tables.TOOL;
import static ro.prospero.aidir.jooq.generated.Tables.TOOL_ATTRIBUTE_OPTION;

/**
 * The directory browse query: filtering, counting, faceting and paging, in SQL.
 *
 * <p>Lucene is asked for one thing only - which tools match the text, and in what order - and everything
 * else happens against {@code tool}. That split is deliberate. The index is in-memory and rebuilt on
 * startup, so it is the wrong thing to count a catalogue against; and the page needs an exact total and an
 * offset, which the index gives up far less readily than a {@code COUNT(*)} does.
 *
 * <p>Facet counts are computed against the <em>other</em> active filters, not against all of them: the
 * category counts obey every other filter and the text, but not the category selection itself. Counting a
 * dimension against its own filter is how a sidebar ends up showing (0) beside everything the user has not
 * picked.
 */
@Service
public class ToolBrowseService {

    /**
     * How deep into the ranked hits the filters are allowed to reach. The text query runs before any
     * category or pricing predicate, so this is the candidate set those predicates then narrow - too small
     * and a filtered search silently loses matches that were ranked just outside it.
     */
    private static final int CANDIDATE_LIMIT = 500;

    private static final String FACET_ALIAS = "expanded_value";
    private static final Field<String> FACET_VALUE = DSL.field(DSL.name(FACET_ALIAS, "value"), String.class);
    private static final String INTEGRATION_ATTRIBUTE = "integration";

    private final DSLContext dslContext;
    private final DocumentSearchService documentSearchService;
    private final ToolCards toolCards;

    public ToolBrowseService(DSLContext dslContext,
                             DocumentSearchService documentSearchService,
                             ToolCards toolCards) {
        this.dslContext = dslContext;
        this.documentSearchService = documentSearchService;
        this.toolCards = toolCards;
    }

    public ToolPage browse(ToolBrowseQuery query) {
        requireKnownPricing(query.selected(ToolFilter.PRICING));

        List<Long> rankedIds = query.hasQuery() ? rankedIds(query.query()) : List.of();

        // Nothing matched the text, so nothing can survive the filters either - and an empty IN () list
        // would otherwise widen the query back out to the whole catalogue.
        if (query.hasQuery() && rankedIds.isEmpty()) {
            return new ToolPage(List.of(), 0, query.page(), query.size(), Map.of());
        }

        Condition base = baseCondition(rankedIds, query.hasQuery());
        Map<ToolFilter, Condition> conditions = new EnumMap<>(ToolFilter.class);
        for (ToolFilter filter : ToolFilter.values()) {
            conditions.put(filter, filter == ToolFilter.PRICING
                                   ? pricingCondition(query.selected(filter), query.freeTrial())
                                   : ToolConditions.containsAny(column(filter), query.selected(filter)));
        }
        Condition all = conditions.values().stream().reduce(base, Condition::and);

        long total = dslContext.selectCount()
                               .from(TOOL)
                               .where(all)
                               .fetchOne(0, long.class);

        List<ToolRecord> rows = dslContext.selectFrom(TOOL)
                                          .where(all)
                                          .orderBy(ordering(query, rankedIds))
                                          .limit(query.size())
                                          .offset(query.offset())
                                          .fetch();

        return new ToolPage(toolCards.toCards(rows),
                            total,
                            query.page(),
                            query.size(),
                            facets(base, conditions, query));
    }

    /**
     * With the trial toggle on, a pricing model only counts through a plan of that type that offers a trial,
     * so choosing Subscription asks for a subscription with a trial rather than for either one.
     */
    private static Condition pricingCondition(List<String> pricing, boolean freeTrial) {
        if (!freeTrial) {
            return ToolConditions.containsAny(TOOL.PRICING_MODELS, pricing);
        }
        return pricing.isEmpty() ? ToolConditions.hasTrial() : ToolConditions.hasTrialOn(pricing);
    }

    private Map<String, List<FacetCount>> facets(Condition base,
                                                 Map<ToolFilter, Condition> conditions,
                                                 ToolBrowseQuery query) {
        Map<String, List<FacetCount>> facets = new LinkedHashMap<>();
        for (ToolFilter filter : ToolFilter.values()) {
            Condition others = allExcept(filter, base, conditions);
            facets.put(filter.param(), filter == ToolFilter.PRICING && query.freeTrial()
                                       ? trialCountsByPaymentType(others)
                                       : facetCounts(column(filter), scoped(filter, others)));
        }

        // What turning the toggle on would leave, so it respects the pricing models already chosen.
        Condition withTrial = allExcept(ToolFilter.PRICING, base, conditions)
                .and(pricingCondition(query.selected(ToolFilter.PRICING), true));
        long trialCount = dslContext.selectCount().from(TOOL).where(withTrial).fetchOne(0, long.class);
        facets.put(ToolBrowseQuery.FREE_TRIAL_PARAM, List.of(new FacetCount("true", trialCount)));
        return facets;
    }

    private static Condition allExcept(ToolFilter filter, Condition base, Map<ToolFilter, Condition> conditions) {
        return conditions.entrySet()
                         .stream()
                         .filter(entry -> entry.getKey() != filter)
                         .map(Map.Entry::getValue)
                         .reduce(base, Condition::and);
    }

    private List<FacetCount> trialCountsByPaymentType(Condition condition) {
        List<String> types = Arrays.stream(PaymentType.values()).map(PaymentType::wire).toList();
        List<Field<Integer>> counts = types.stream()
                                           .<Field<Integer>>map(type -> DSL.count().filterWhere(
                                                   ToolConditions.hasTrialOn(List.of(type))))
                                           .toList();
        Record row = dslContext.select(counts).from(TOOL).where(condition).fetchOne();
        return IntStream.range(0, types.size())
                        .mapToObj(i -> new FacetCount(types.get(i), row.get(i, long.class)))
                        .toList();
    }

    /**
     * A vendor's own integrations are shown on the listing, but only the shared list is offered as a filter,
     * and the trial flag stored among the pricing models has a toggle rather than a row.
     */
    private Condition scoped(ToolFilter filter, Condition condition) {
        return switch (filter) {
            case INTEGRATION -> condition.and(FACET_VALUE.in(
                    DSL.select(TOOL_ATTRIBUTE_OPTION.VALUE)
                       .from(TOOL_ATTRIBUTE_OPTION)
                       .where(TOOL_ATTRIBUTE_OPTION.ATTRIBUTE.eq(INTEGRATION_ATTRIBUTE))));
            case PRICING -> condition.and(FACET_VALUE.ne(ToolPricing.FREE_TRIAL));
            default -> condition;
        };
    }

    private static Field<JSONB> column(ToolFilter filter) {
        return switch (filter) {
            case CATEGORY -> TOOL.CATEGORIES;
            case PRICING -> TOOL.PRICING_MODELS;
            case PRICE_RANGE -> TOOL.PRICE_RANGES;
            case PLATFORM -> TOOL.PLATFORMS;
            case TARGET_USER -> TOOL.TARGET_USERS;
            case LANGUAGE -> TOOL.LANGUAGES;
            case AI_TECHNOLOGY -> TOOL.AI_TECHNOLOGIES;
            case DEPLOYMENT -> TOOL.DEPLOYMENTS;
            case INTEGRATION -> TOOL.INTEGRATIONS;
            case INDUSTRY -> TOOL.INDUSTRIES;
        };
    }

    private void requireKnownPricing(List<String> pricing) {
        if (pricing.isEmpty()) {
            return;
        }
        Set<String> known = dslContext.select(PRICING.TYPE).from(PRICING).fetchSet(PRICING.TYPE);
        if (!known.containsAll(pricing)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown pricing model in " + pricing);
        }
    }

    /**
     * The whole category vocabulary with its published tool counts, empty categories included.
     *
     * <p>Unlike the facets on a browse response, this list does not shrink: it is what a navigation menu
     * reads, and a category disappearing from the menu the moment its last tool is unpublished would be a
     * worse answer than showing it with a zero.
     */
    public List<CategoryCount> categoryCounts() {
        Map<String, Long> counts = facetCounts(TOOL.CATEGORIES, TOOL.PUBLISHED.isTrue())
                .stream()
                .collect(Collectors.toMap(FacetCount::value, FacetCount::count));

        return dslContext.select(CATEGORY.NAME, CATEGORY.DESCRIPTION)
                         .from(CATEGORY)
                         .orderBy(CATEGORY.NAME.asc())
                         .fetch(r -> new CategoryCount(r.value1(), r.value2(), counts.getOrDefault(r.value1(), 0L)));
    }

    private List<Long> rankedIds(String queryText) {
        return documentSearchService.search(queryText, CANDIDATE_LIMIT)
                                    .stream()
                                    .map(ToolSearchHit::getId)
                                    .toList();
    }

    private Condition baseCondition(List<Long> rankedIds, boolean hasQuery) {
        Condition published = TOOL.PUBLISHED.isTrue();
        return hasQuery ? published.and(TOOL.ID.in(rankedIds)) : published;
    }

    private OrderField<?>[] ordering(ToolBrowseQuery query, List<Long> rankedIds) {
        // Every ordering ends on the id so that paging is stable: without a unique tiebreaker, two tools
        // approved in the same instant can swap places between page 1 and page 2 and one of them is never
        // seen.
        return switch (query.sort()) {
            case RELEVANCE -> new OrderField<?>[]{rank(rankedIds).asc(), TOOL.ID.desc()};
            case PRICE -> new OrderField<?>[]{TOOL.LOWEST_MONTHLY_CENTS.asc().nullsLast(),
                                              TOOL.APPROVED_AT.desc(),
                                              TOOL.ID.desc()};
            case NEWEST -> new OrderField<?>[]{TOOL.APPROVED_AT.desc(), TOOL.ID.desc()};
        };
    }

    /**
     * Lucene ranked the ids; this replays that order in SQL so the page can still be cut with LIMIT and
     * OFFSET rather than by fetching every match and slicing in memory.
     */
    private Field<Integer> rank(List<Long> rankedIds) {
        return DSL.field("array_position({0}, {1})",
                         Integer.class,
                         DSL.val(rankedIds.toArray(Long[]::new)),
                         TOOL.ID);
    }

    private List<FacetCount> facetCounts(Field<JSONB> column, Condition condition) {
        // The column is a JSONB array, so it has to be expanded into rows before it can be grouped.
        // COALESCE keeps tools with an empty column from dropping the row entirely.
        Table<Record> expanded = DSL.table("jsonb_array_elements_text(coalesce({0}, '[]'::jsonb))", column)
                                    .as(FACET_ALIAS, "value");

        return dslContext.select(FACET_VALUE, DSL.count())
                         .from(TOOL)
                         .crossJoin(expanded)
                         .where(condition)
                         .groupBy(FACET_VALUE)
                         .orderBy(FACET_VALUE.asc())
                         .fetch(r -> new FacetCount(r.value1(), r.value2()));
    }

}
