package ro.prospero.aidir.service;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import ro.prospero.aidir.data.FacetCount;
import ro.prospero.aidir.data.ToolBrowseQuery;
import ro.prospero.aidir.data.ToolPage;
import ro.prospero.aidir.data.ToolSearchHit;
import ro.prospero.aidir.jooq.generated.public_.tables.records.ToolRecord;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static ro.prospero.aidir.jooq.generated.public_.Tables.CATEGORY;
import static ro.prospero.aidir.jooq.generated.public_.Tables.TOOL;

/**
 * The directory browse query: filtering, counting, faceting and paging, in SQL.
 *
 * <p>Lucene is asked for one thing only - which tools match the text, and in what order - and everything
 * else happens against {@code tool}. That split is deliberate. The index is in-memory and rebuilt on
 * startup, so it is the wrong thing to count a catalogue against; and the page needs an exact total and an
 * offset, which the index gives up far less readily than a {@code COUNT(*)} does.
 *
 * <p>Facet counts are computed against the <em>other</em> active filters, not against all of them: the
 * category counts obey the pricing filter and the text, but not the category selection itself. Counting a
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
        List<Long> rankedIds = query.hasQuery() ? rankedIds(query.query()) : List.of();

        // Nothing matched the text, so nothing can survive the filters either - and an empty IN () list
        // would otherwise widen the query back out to the whole catalogue.
        if (query.hasQuery() && rankedIds.isEmpty()) {
            return new ToolPage(List.of(), 0, query.page(), query.size(), List.of(), List.of());
        }

        Condition base = baseCondition(rankedIds, query.hasQuery());
        Condition categories = ToolConditions.inCategories(query.categories());
        Condition pricing = pricingCondition(query.pricing());
        Condition all = base.and(categories).and(pricing);

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
                            categoryFacets(base.and(pricing)),
                            pricingFacets(base.and(categories)));
    }

    /**
     * The whole category vocabulary with its published tool counts, empty categories included.
     *
     * <p>Unlike the facets on a browse response, this list does not shrink: it is what a navigation menu
     * reads, and a category disappearing from the menu the moment its last tool is unpublished would be a
     * worse answer than showing it with a zero.
     */
    public List<FacetCount> categoryCounts() {
        Map<String, Long> counts = categoryFacets(TOOL.PUBLISHED.isTrue())
                .stream()
                .collect(Collectors.toMap(FacetCount::value, FacetCount::count));

        return dslContext.select(CATEGORY.NAME)
                         .from(CATEGORY)
                         .orderBy(CATEGORY.NAME.asc())
                         .fetch(r -> new FacetCount(r.value1(), counts.getOrDefault(r.value1(), 0L)));
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

    private Condition pricingCondition(List<String> pricing) {
        return pricing.isEmpty() ? DSL.noCondition() : TOOL.PRICING.in(pricing);
    }

    private OrderField<?>[] ordering(ToolBrowseQuery query, List<Long> rankedIds) {
        // Every ordering ends on the id so that paging is stable: without a unique tiebreaker, two tools
        // approved in the same instant can swap places between page 1 and page 2 and one of them is never
        // seen.
        return switch (query.sort()) {
            case RELEVANCE -> new OrderField<?>[]{rank(rankedIds).asc(), TOOL.ID.desc()};
            case PRICE -> new OrderField<?>[]{priceOrder().asc(), TOOL.APPROVED_AT.desc(), TOOL.ID.desc()};
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

    private Field<Integer> priceOrder() {
        return DSL.choose(TOOL.PRICING)
                  .when("free", 0)
                  .when("freemium", 1)
                  .otherwise(2);
    }

    private List<FacetCount> categoryFacets(Condition condition) {
        // categories is a JSONB array, so it has to be expanded into rows before it can be grouped.
        // COALESCE keeps tools with no categories from dropping the row entirely.
        Table<Record> expanded = DSL.table("jsonb_array_elements_text(coalesce({0}, '[]'::jsonb))",
                                           TOOL.CATEGORIES)
                                    .as("expanded_category", "value");
        Field<String> category = DSL.field(DSL.name("expanded_category", "value"), String.class);

        return dslContext.select(category, DSL.count())
                         .from(TOOL)
                         .crossJoin(expanded)
                         .where(condition)
                         .groupBy(category)
                         .orderBy(category.asc())
                         .fetch(r -> new FacetCount(r.value1(), r.value2()));
    }

    private List<FacetCount> pricingFacets(Condition condition) {
        return dslContext.select(TOOL.PRICING, DSL.count())
                         .from(TOOL)
                         .where(condition)
                         .groupBy(TOOL.PRICING)
                         .orderBy(priceOrder().asc())
                         .fetch(r -> new FacetCount(r.value1(), r.value2()));
    }

}
