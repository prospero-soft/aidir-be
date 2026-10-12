package ro.prospero.aidir.service;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ro.prospero.aidir.data.TalentDetailsDTO;
import ro.prospero.aidir.data.TalentProfileEdit;
import ro.prospero.aidir.data.UserOnboardingPayload;

import java.util.function.BiConsumer;

import static ro.prospero.aidir.jooq.generated.public_.Tables.ACCOUNT;
import static ro.prospero.aidir.jooq.generated.public_.Tables.TALENT_PROFILE;
import static ro.prospero.aidir.service.TalentProfileSections.blankToNull;

/**
 * The Resume / CV page's writes: one section of the signed-in talent's own profile at a time.
 *
 * <p>Each answers with the whole profile as it now reads, so the page can redraw without asking again. That
 * read happens after the transaction commits, because {@link TalentProfileService} reads through the shared
 * DSLContext on another connection. Transactions are jOOQ's for the reason {@link UserOnboardingService} gives.
 */
@Service
public class TalentProfileEditService {

    private final DSLContext dslContext;
    private final TalentProfileSections sections;
    private final TalentProfileService talentProfileService;

    public TalentProfileEditService(DSLContext dslContext,
                                    TalentProfileSections sections,
                                    TalentProfileService talentProfileService) {
        this.dslContext = dslContext;
        this.sections = sections;
        this.talentProfileService = talentProfileService;
    }

    /** The account's display name is the same person's name, so the navbar would go stale without the second update. */
    public TalentDetailsDTO updateGeneral(long accountId, TalentProfileEdit.General general) {
        return write(accountId, (tx, id) -> {
            tx.update(TALENT_PROFILE)
              .set(TALENT_PROFILE.FULL_NAME, general.fullName())
              .set(TALENT_PROFILE.COUNTRY, general.country())
              .set(TALENT_PROFILE.TITLE, general.title())
              .set(TALENT_PROFILE.SHORT_DESCRIPTION, blankToNull(general.shortDescription()))
              .where(TALENT_PROFILE.ACCOUNT_ID.eq(id))
              .execute();
            tx.update(ACCOUNT)
              .set(ACCOUNT.DISPLAY_NAME, general.fullName())
              .where(ACCOUNT.ID.eq(id))
              .execute();
        });
    }

    public TalentDetailsDTO updateSummary(long accountId, TalentProfileEdit.Summary summary) {
        return write(accountId, (tx, id) -> tx.update(TALENT_PROFILE)
                                              .set(TALENT_PROFILE.SUMMARY, summary.summary())
                                              .where(TALENT_PROFILE.ACCOUNT_ID.eq(id))
                                              .execute());
    }

    public TalentDetailsDTO updateWork(long accountId, UserOnboardingPayload.WorkPreferences work) {
        return write(accountId, (tx, id) -> tx.update(TALENT_PROFILE)
                                              .set(TALENT_PROFILE.WORK_LOCATION, work.location())
                                              .set(TALENT_PROFILE.WORK_RELOCATION, work.relocation())
                                              .set(TALENT_PROFILE.WORK_WORKPLACE, work.workplace())
                                              .set(TALENT_PROFILE.WORK_EMPLOYMENT_TYPE, work.employmentType())
                                              .where(TALENT_PROFILE.ACCOUNT_ID.eq(id))
                                              .execute());
    }

    /** An emptied field clears its link: the public profile renders a button for every non-null one. */
    public TalentDetailsDTO updateLinks(long accountId, UserOnboardingPayload.ProfileLinks links) {
        return write(accountId, (tx, id) -> tx.update(TALENT_PROFILE)
                                              .set(TALENT_PROFILE.LINK_LINKEDIN, blankToNull(links.linkedin()))
                                              .set(TALENT_PROFILE.LINK_GITHUB, blankToNull(links.github()))
                                              .set(TALENT_PROFILE.LINK_PORTFOLIO, blankToNull(links.portfolio()))
                                              .set(TALENT_PROFILE.LINK_OTHER, blankToNull(links.other()))
                                              .where(TALENT_PROFILE.ACCOUNT_ID.eq(id))
                                              .execute());
    }

    public TalentDetailsDTO replaceSkills(long accountId, TalentProfileEdit.Skills skills) {
        return write(accountId, (tx, id) -> sections.replaceSkills(tx, id, skills.skills()));
    }

    public TalentDetailsDTO replaceLanguages(long accountId, TalentProfileEdit.Languages languages) {
        return write(accountId, (tx, id) -> sections.replaceLanguages(tx, id, languages.languages()));
    }

    public TalentDetailsDTO replaceExperience(long accountId, TalentProfileEdit.Experience experience) {
        return write(accountId, (tx, id) -> sections.replaceExperience(tx, id, experience.experience()));
    }

    public TalentDetailsDTO replaceEducation(long accountId, TalentProfileEdit.Education education) {
        return write(accountId, (tx, id) -> sections.replaceEducation(tx, id, education.education()));
    }

    public TalentDetailsDTO replaceCertifications(long accountId, TalentProfileEdit.Certifications certifications) {
        return write(accountId, (tx, id) -> sections.replaceCertifications(tx, id, certifications.certifications()));
    }

    public TalentDetailsDTO replaceCourses(long accountId, TalentProfileEdit.Courses courses) {
        return write(accountId, (tx, id) -> sections.replaceCourses(tx, id, courses.courses()));
    }

    public TalentDetailsDTO replaceProjects(long accountId, TalentProfileEdit.Projects projects) {
        return write(accountId, (tx, id) -> sections.replaceProjects(tx, id, projects.projects()));
    }

    /**
     * A free-tier talent, a vendor or an admin is signed in without a talent_profile row, and has nothing
     * here to edit - a 404 says so rather than letting the child-table inserts fail on their foreign key.
     */
    private TalentDetailsDTO write(long accountId, BiConsumer<DSLContext, Long> change) {
        dslContext.transaction(cfg -> {
            DSLContext tx = DSL.using(cfg);
            boolean hasProfile = tx.fetchExists(TALENT_PROFILE, TALENT_PROFILE.ACCOUNT_ID.eq(accountId));
            if (!hasProfile) {
                throw notFound(accountId);
            }
            change.accept(tx, accountId);
        });
        return talentProfileService.find(accountId).orElseThrow(() -> notFound(accountId));
    }

    private static ResponseStatusException notFound(long accountId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Account " + accountId + " has no talent profile");
    }
}
