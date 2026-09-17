package ro.prospero.aidir.service;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.springframework.stereotype.Component;
import ro.prospero.aidir.data.TalentCardDTO;
import ro.prospero.aidir.jooq.generated.public_.tables.records.TalentProfileRecord;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ro.prospero.aidir.jooq.generated.public_.Tables.TALENT_LANGUAGE;
import static ro.prospero.aidir.jooq.generated.public_.Tables.TALENT_SKILL;

/** Builds directory cards out of profile rows, for the directory itself and for the similar strip on a profile. */
@Component
public class TalentCards {

    private final DSLContext dslContext;

    public TalentCards(DSLContext dslContext) {
        this.dslContext = dslContext;
    }

    /** Skills and languages are read for the whole set in one query each, not one per row. */
    public List<TalentCardDTO> toCards(List<TalentProfileRecord> rows) {
        List<Long> ids = rows.stream().map(TalentProfileRecord::getAccountId).toList();
        Map<Long, List<String>> skills = groupedValues(TALENT_SKILL,
                                                       TALENT_SKILL.ACCOUNT_ID,
                                                       TALENT_SKILL.SKILL,
                                                       ids);
        Map<Long, List<String>> languages = groupedValues(TALENT_LANGUAGE,
                                                          TALENT_LANGUAGE.ACCOUNT_ID,
                                                          TALENT_LANGUAGE.LANGUAGE,
                                                          ids);

        return rows.stream()
                   .map(row -> new TalentCardDTO(row.getAccountId(),
                                                 row.getFullName(),
                                                 row.getTitle(),
                                                 row.getShortDescription(),
                                                 row.getSummary(),
                                                 row.getCountry(),
                                                 row.getWorkLocation(),
                                                 row.getWorkRelocation(),
                                                 row.getWorkWorkplace(),
                                                 row.getWorkEmploymentType(),
                                                 skills.getOrDefault(row.getAccountId(), List.of()),
                                                 languages.getOrDefault(row.getAccountId(), List.of()),
                                                 row.getCreatedAt()))
                   .toList();
    }

    private Map<Long, List<String>> groupedValues(Table<?> table,
                                                  Field<Long> accountId,
                                                  Field<String> value,
                                                  List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<String>> grouped = new HashMap<>();
        dslContext.select(accountId, value)
                  .from(table)
                  .where(accountId.in(ids))
                  .orderBy(accountId.asc(), value.asc())
                  .forEach(r -> grouped.computeIfAbsent(r.value1(), k -> new ArrayList<>()).add(r.value2()));
        return grouped;
    }
}
