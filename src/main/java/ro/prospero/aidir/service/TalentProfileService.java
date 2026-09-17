package ro.prospero.aidir.service;

import org.jooq.DSLContext;
import org.jooq.Record2;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import ro.prospero.aidir.data.TalentCardDTO;
import ro.prospero.aidir.data.TalentDetailsDTO;
import ro.prospero.aidir.jooq.generated.public_.tables.records.TalentProfileRecord;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static ro.prospero.aidir.jooq.generated.public_.Tables.TALENT_CERTIFICATION;
import static ro.prospero.aidir.jooq.generated.public_.Tables.TALENT_COURSE;
import static ro.prospero.aidir.jooq.generated.public_.Tables.TALENT_EDUCATION;
import static ro.prospero.aidir.jooq.generated.public_.Tables.TALENT_EXPERIENCE;
import static ro.prospero.aidir.jooq.generated.public_.Tables.TALENT_LANGUAGE;
import static ro.prospero.aidir.jooq.generated.public_.Tables.TALENT_PROFILE;
import static ro.prospero.aidir.jooq.generated.public_.Tables.TALENT_PROJECT;
import static ro.prospero.aidir.jooq.generated.public_.Tables.TALENT_SKILL;

/**
 * One public talent profile, read whole.
 *
 * <p>Every history section comes back in {@code display_order}, which is the order the onboarding wizard
 * was filled in - the rows carry no dates that could be sorted on instead.
 */
@Service
public class TalentProfileService {

    /** How many similar profiles the strip at the foot of the page shows. */
    private static final int SIMILAR_LIMIT = 5;

    private final DSLContext dslContext;
    private final TalentCards talentCards;

    public TalentProfileService(DSLContext dslContext, TalentCards talentCards) {
        this.dslContext = dslContext;
        this.talentCards = talentCards;
    }

    public Optional<TalentDetailsDTO> find(long accountId) {
        return dslContext.selectFrom(TALENT_PROFILE)
                         .where(TALENT_PROFILE.ACCOUNT_ID.eq(accountId))
                         .fetchOptional()
                         .map(this::toDetails);
    }

    private TalentDetailsDTO toDetails(TalentProfileRecord profile) {
        long accountId = profile.getAccountId();
        TalentCardDTO card = talentCards.toCards(List.of(profile)).getFirst();

        return new TalentDetailsDTO(card,
                                    new TalentDetailsDTO.Links(profile.getLinkLinkedin(),
                                                               profile.getLinkGithub(),
                                                               profile.getLinkPortfolio(),
                                                               profile.getLinkOther()),
                                    languages(accountId),
                                    experience(accountId),
                                    education(accountId),
                                    certifications(accountId),
                                    courses(accountId),
                                    projects(accountId),
                                    similar(accountId, card.skills()));
    }

    private List<TalentDetailsDTO.Language> languages(long accountId) {
        return dslContext.selectFrom(TALENT_LANGUAGE)
                         .where(TALENT_LANGUAGE.ACCOUNT_ID.eq(accountId))
                         .orderBy(TALENT_LANGUAGE.DISPLAY_ORDER.asc(), TALENT_LANGUAGE.LANGUAGE.asc())
                         .fetch(r -> new TalentDetailsDTO.Language(r.getLanguage(), r.getProficiency()));
    }

    private List<TalentDetailsDTO.Experience> experience(long accountId) {
        return dslContext.selectFrom(TALENT_EXPERIENCE)
                         .where(TALENT_EXPERIENCE.ACCOUNT_ID.eq(accountId))
                         .orderBy(TALENT_EXPERIENCE.DISPLAY_ORDER.asc(), TALENT_EXPERIENCE.ID.asc())
                         .fetch(r -> new TalentDetailsDTO.Experience(r.getId(),
                                                                     r.getCompany(),
                                                                     r.getPosition(),
                                                                     r.getLocation(),
                                                                     r.getWorkplace(),
                                                                     r.getPeriod(),
                                                                     r.getDescription()));
    }

    private List<TalentDetailsDTO.Education> education(long accountId) {
        return dslContext.selectFrom(TALENT_EDUCATION)
                         .where(TALENT_EDUCATION.ACCOUNT_ID.eq(accountId))
                         .orderBy(TALENT_EDUCATION.DISPLAY_ORDER.asc(), TALENT_EDUCATION.ID.asc())
                         .fetch(r -> new TalentDetailsDTO.Education(r.getId(),
                                                                    r.getInstitution(),
                                                                    r.getDegree(),
                                                                    r.getField(),
                                                                    r.getGraduationYear(),
                                                                    r.getLocation()));
    }

    private List<TalentDetailsDTO.Certification> certifications(long accountId) {
        return dslContext.selectFrom(TALENT_CERTIFICATION)
                         .where(TALENT_CERTIFICATION.ACCOUNT_ID.eq(accountId))
                         .orderBy(TALENT_CERTIFICATION.DISPLAY_ORDER.asc(), TALENT_CERTIFICATION.ID.asc())
                         .fetch(r -> new TalentDetailsDTO.Certification(r.getId(),
                                                                        r.getName(),
                                                                        r.getOrganization(),
                                                                        r.getYear()));
    }

    private List<TalentDetailsDTO.Course> courses(long accountId) {
        return dslContext.selectFrom(TALENT_COURSE)
                         .where(TALENT_COURSE.ACCOUNT_ID.eq(accountId))
                         .orderBy(TALENT_COURSE.DISPLAY_ORDER.asc(), TALENT_COURSE.ID.asc())
                         .fetch(r -> new TalentDetailsDTO.Course(r.getId(),
                                                                 r.getName(),
                                                                 r.getProvider(),
                                                                 r.getFocus(),
                                                                 r.getCompletionYear()));
    }

    private List<TalentDetailsDTO.Project> projects(long accountId) {
        return dslContext.selectFrom(TALENT_PROJECT)
                         .where(TALENT_PROJECT.ACCOUNT_ID.eq(accountId))
                         .orderBy(TALENT_PROJECT.DISPLAY_ORDER.asc(), TALENT_PROJECT.ID.asc())
                         .fetch(r -> new TalentDetailsDTO.Project(r.getId(),
                                                                  r.getTitle(),
                                                                  r.getCategory(),
                                                                  r.getYear(),
                                                                  r.getDescription()));
    }

    /**
     * The profiles sharing the most skills with this one. Shared skills are the only similarity signal the
     * schema carries: there is no industry, seniority or rate to compare on.
     *
     * <p>Someone with no skills listed, or with none in common with anybody, gets an empty strip rather
     * than a filler row of whoever happens to be newest.
     */
    private List<TalentCardDTO> similar(long accountId, List<String> skills) {
        if (skills.isEmpty()) {
            return List.of();
        }

        List<Long> ranked = dslContext.select(TALENT_SKILL.ACCOUNT_ID, DSL.count())
                                      .from(TALENT_SKILL)
                                      .where(TALENT_SKILL.SKILL.in(skills))
                                      .and(TALENT_SKILL.ACCOUNT_ID.ne(accountId))
                                      .groupBy(TALENT_SKILL.ACCOUNT_ID)
                                      .orderBy(DSL.count().desc(), TALENT_SKILL.ACCOUNT_ID.desc())
                                      .limit(SIMILAR_LIMIT)
                                      .fetch(Record2::value1);

        if (ranked.isEmpty()) {
            return List.of();
        }

        // The ranking is by overlap, which the profile fetch cannot reproduce from a plain IN, so the rows
        // come back in whatever order the second query gives them and are put back in rank here.
        Map<Long, TalentCardDTO> byId = talentCards.toCards(dslContext.selectFrom(TALENT_PROFILE)
                                                                      .where(TALENT_PROFILE.ACCOUNT_ID.in(ranked))
                                                                      .fetch())
                                                   .stream()
                                                   .collect(Collectors.toMap(TalentCardDTO::id, c -> c));
        return ranked.stream().map(byId::get).filter(Objects::nonNull).toList();
    }
}
