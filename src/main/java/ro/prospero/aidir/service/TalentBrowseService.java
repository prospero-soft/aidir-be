package ro.prospero.aidir.service;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import ro.prospero.aidir.data.FacetCount;
import ro.prospero.aidir.data.TalentBrowseQuery;
import ro.prospero.aidir.data.TalentLocationScope;
import ro.prospero.aidir.data.TalentPage;
import ro.prospero.aidir.jooq.generated.public_.tables.records.TalentProfileRecord;

import java.util.List;

import static ro.prospero.aidir.jooq.generated.public_.Tables.TALENT_LANGUAGE;
import static ro.prospero.aidir.jooq.generated.public_.Tables.TALENT_PROFILE;
import static ro.prospero.aidir.jooq.generated.public_.Tables.TALENT_SKILL;

/**
 * The talent directory browse query: filtering, counting, faceting and paging, in SQL.
 *
 * <p>Unlike {@link ToolBrowseService} there is no index in front of this. Lucene holds tools only, so the
 * text filter here is a set of {@code ILIKE} predicates against the profile's own columns and its skills.
 * That is also why there is no relevance ordering: nothing produced a score to order by.
 *
 * <p>Facet counts are computed against the <em>other</em> active filters, not against all of them - the
 * workplace counts obey the skills filter and the text, but not the workplace selection itself. Counting a
 * dimension against its own filter is how a sidebar ends up showing (0) beside everything the user has not
 * picked.
 */
@Service
public class TalentBrowseService {

    /** What {@code work_relocation} holds for someone who will not move. Anything else is willingness. */
    private static final String NOT_OPEN_TO_RELOCATION = "Not open to relocation";

    /** How many facet rows a dimension may return. Skills alone can run to the whole vocabulary. */
    private static final int FACET_LIMIT = 50;

    private final DSLContext dslContext;
    private final TalentCards talentCards;

    public TalentBrowseService(DSLContext dslContext, TalentCards talentCards) {
        this.dslContext = dslContext;
        this.talentCards = talentCards;
    }

    public TalentPage browse(TalentBrowseQuery query) {
        Condition text = textCondition(query);
        Condition locations = locationCondition(query.locations(), query.locationScope());
        Condition workplace = eq(TALENT_PROFILE.WORK_WORKPLACE, query.workplace());
        Condition employment = eq(TALENT_PROFILE.WORK_EMPLOYMENT_TYPE, query.employmentType());
        Condition skills = allOf(TALENT_SKILL, TALENT_SKILL.ACCOUNT_ID, TALENT_SKILL.SKILL, query.skills());
        Condition languages = allOf(TALENT_LANGUAGE,
                                    TALENT_LANGUAGE.ACCOUNT_ID,
                                    TALENT_LANGUAGE.LANGUAGE,
                                    query.languages());

        Condition all = text.and(locations).and(workplace).and(employment).and(skills).and(languages);

        long total = dslContext.selectCount()
                               .from(TALENT_PROFILE)
                               .where(all)
                               .fetchOne(0, long.class);

        List<TalentProfileRecord> rows = dslContext.selectFrom(TALENT_PROFILE)
                                                   .where(all)
                                                   .orderBy(ordering(query))
                                                   .limit(query.size())
                                                   .offset(query.offset())
                                                   .fetch();

        Condition withoutWorkplace = text.and(locations).and(employment).and(skills).and(languages);
        Condition withoutEmployment = text.and(locations).and(workplace).and(skills).and(languages);
        Condition withoutSkills = text.and(locations).and(workplace).and(employment).and(languages);
        Condition withoutLanguages = text.and(locations).and(workplace).and(employment).and(skills);
        Condition withoutLocations = text.and(workplace).and(employment).and(skills).and(languages);

        return new TalentPage(talentCards.toCards(rows),
                              total,
                              query.page(),
                              query.size(),
                              columnFacets(TALENT_PROFILE.WORK_WORKPLACE, withoutWorkplace),
                              columnFacets(TALENT_PROFILE.WORK_EMPLOYMENT_TYPE, withoutEmployment),
                              joinedFacets(TALENT_SKILL,
                                           TALENT_SKILL.ACCOUNT_ID,
                                           TALENT_SKILL.SKILL,
                                           withoutSkills),
                              joinedFacets(TALENT_LANGUAGE,
                                           TALENT_LANGUAGE.ACCOUNT_ID,
                                           TALENT_LANGUAGE.LANGUAGE,
                                           withoutLanguages),
                              columnFacets(TALENT_PROFILE.WORK_LOCATION, withoutLocations));
    }

    /**
     * The free-text filter. {@code query} is one substring match across the profile's prose and its skills;
     * {@code keywords} are the same match repeated and AND-ed, because a list of keywords narrows rather than
     * widens; {@code jobTitle} is deliberately narrower and only looks at the title.
     */
    private Condition textCondition(TalentBrowseQuery query) {
        Condition condition = DSL.noCondition();

        if (query.hasQuery()) {
            condition = condition.and(matchesText(query.query()));
        }
        for (String keyword : query.keywords()) {
            condition = condition.and(matchesText(keyword));
        }
        if (query.jobTitle() != null) {
            condition = condition.and(like(TALENT_PROFILE.TITLE, query.jobTitle()));
        }
        return condition;
    }

    private Condition matchesText(String text) {
        return like(TALENT_PROFILE.FULL_NAME, text)
                .or(like(TALENT_PROFILE.TITLE, text))
                .or(like(TALENT_PROFILE.SHORT_DESCRIPTION, text))
                .or(like(TALENT_PROFILE.SUMMARY, text))
                .or(DSL.exists(dslContext.selectOne()
                                         .from(TALENT_SKILL)
                                         .where(TALENT_SKILL.ACCOUNT_ID.eq(TALENT_PROFILE.ACCOUNT_ID))
                                         .and(like(TALENT_SKILL.SKILL, text))));
    }

    /**
     * A profile holds one location, so the scope is what decides whether the selected ones are the whole
     * question. RELOCATE_ONLY drops them entirely rather than intersecting: asking for people willing to move
     * to you is not the same as asking where they are now.
     */
    private Condition locationCondition(List<String> locations, TalentLocationScope scope) {
        Condition here = locations.isEmpty()
                ? DSL.noCondition()
                : TALENT_PROFILE.WORK_LOCATION.in(locations);
        Condition willMove = TALENT_PROFILE.WORK_RELOCATION.ne(NOT_OPEN_TO_RELOCATION);

        return switch (scope) {
            case CURRENT -> here;
            case CURRENT_OR_RELOCATE -> locations.isEmpty() ? DSL.noCondition() : here.or(willMove);
            case RELOCATE_ONLY -> willMove;
        };
    }

    /**
     * Every selected value must be present, so each gets its own EXISTS rather than one IN: ticking Python
     * and PyTorch asks for somebody who has both. Note this is the opposite of
     * {@code ToolBrowseService.categoryCondition}, where two ticked categories mean either - a tool is one
     * thing or another, a person is the sum of what they know.
     */
    private Condition allOf(Table<?> table,
                            Field<Long> accountId,
                            Field<String> value,
                            List<String> selected) {
        Condition condition = DSL.noCondition();
        for (String each : selected) {
            condition = condition.and(DSL.exists(dslContext.selectOne()
                                                           .from(table)
                                                           .where(accountId.eq(TALENT_PROFILE.ACCOUNT_ID))
                                                           .and(value.eq(each))));
        }
        return condition;
    }

    private OrderField<?>[] ordering(TalentBrowseQuery query) {
        // Every ordering ends on the account id so that paging is stable: without a unique tiebreaker, two
        // profiles created in the same instant can swap places between page 1 and page 2 and one of them is
        // never seen.
        return switch (query.sort()) {
            case NAME -> new OrderField<?>[]{TALENT_PROFILE.FULL_NAME.asc(), TALENT_PROFILE.ACCOUNT_ID.desc()};
            case NEWEST -> new OrderField<?>[]{TALENT_PROFILE.CREATED_AT.desc(), TALENT_PROFILE.ACCOUNT_ID.desc()};
        };
    }

    private List<FacetCount> columnFacets(Field<String> column, Condition condition) {
        return dslContext.select(column, DSL.count())
                         .from(TALENT_PROFILE)
                         .where(condition)
                         .groupBy(column)
                         .orderBy(DSL.count().desc(), column.asc())
                         .limit(FACET_LIMIT)
                         .fetch(r -> new FacetCount(r.value1(), r.value2()));
    }

    /**
     * Facets for a dimension that lives in a join table. The profile condition still applies, so this joins
     * rather than counting the join table on its own - otherwise the counts would describe the whole
     * directory instead of the filtered set.
     */
    private List<FacetCount> joinedFacets(Table<?> table,
                                          Field<Long> accountId,
                                          Field<String> value,
                                          Condition condition) {
        return dslContext.select(value, DSL.count())
                         .from(TALENT_PROFILE)
                         .join(table).on(accountId.eq(TALENT_PROFILE.ACCOUNT_ID))
                         .where(condition)
                         .groupBy(value)
                         .orderBy(DSL.count().desc(), value.asc())
                         .limit(FACET_LIMIT)
                         .fetch(r -> new FacetCount(r.value1(), r.value2()));
    }

    private Condition eq(Field<String> column, String value) {
        return value == null ? DSL.noCondition() : column.eq(value);
    }

    /**
     * Case-insensitive substring match. The escaping is what keeps a % or _ typed into the search box from
     * behaving as a wildcard - "100%" should look for "100%", not for everything.
     */
    private Condition like(Field<String> column, String text) {
        String pattern = "%" + text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return column.likeIgnoreCase(pattern, '!');
    }
}
