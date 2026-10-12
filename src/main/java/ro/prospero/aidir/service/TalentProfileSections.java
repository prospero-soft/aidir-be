package ro.prospero.aidir.service;

import org.jooq.DSLContext;
import org.springframework.stereotype.Component;
import ro.prospero.aidir.data.UserOnboardingPayload;

import java.util.List;

import static ro.prospero.aidir.jooq.generated.public_.Tables.TALENT_CERTIFICATION;
import static ro.prospero.aidir.jooq.generated.public_.Tables.TALENT_COURSE;
import static ro.prospero.aidir.jooq.generated.public_.Tables.TALENT_EDUCATION;
import static ro.prospero.aidir.jooq.generated.public_.Tables.TALENT_EXPERIENCE;
import static ro.prospero.aidir.jooq.generated.public_.Tables.TALENT_LANGUAGE;
import static ro.prospero.aidir.jooq.generated.public_.Tables.TALENT_PROJECT;
import static ro.prospero.aidir.jooq.generated.public_.Tables.TALENT_SKILL;

/**
 * Writes the seven list sections hanging off a talent profile, for the onboarding wizard and the Resume / CV
 * editor alike. Every method runs on the transaction it is handed.
 *
 * <p>An edit replaces a whole section - its rows are deleted and the list re-inserted - rather than updating
 * rows in place. Skills and languages have no id to update by, and nothing references the history rows' ids,
 * so a new id on every save costs nothing while display_order is renumbered for free.
 */
@Component
public class TalentProfileSections {

    public void insertSkills(DSLContext tx, long accountId, List<String> skills) {
        for (String skill : skills) {
            tx.insertInto(TALENT_SKILL)
              .set(TALENT_SKILL.ACCOUNT_ID, accountId)
              .set(TALENT_SKILL.SKILL, skill)
              .execute();
        }
    }

    public void insertLanguages(DSLContext tx, long accountId,
                                List<UserOnboardingPayload.LanguageEntry> languages) {
        for (int order = 0; order < size(languages); order++) {
            UserOnboardingPayload.LanguageEntry language = languages.get(order);
            tx.insertInto(TALENT_LANGUAGE)
              .set(TALENT_LANGUAGE.ACCOUNT_ID, accountId)
              .set(TALENT_LANGUAGE.LANGUAGE, language.language())
              .set(TALENT_LANGUAGE.PROFICIENCY, language.proficiency())
              .set(TALENT_LANGUAGE.DISPLAY_ORDER, order)
              .execute();
        }
    }

    public void insertExperience(DSLContext tx, long accountId,
                                 List<UserOnboardingPayload.Experience> experience) {
        for (int order = 0; order < size(experience); order++) {
            UserOnboardingPayload.Experience entry = experience.get(order);
            tx.insertInto(TALENT_EXPERIENCE)
              .set(TALENT_EXPERIENCE.ACCOUNT_ID, accountId)
              .set(TALENT_EXPERIENCE.DISPLAY_ORDER, order)
              .set(TALENT_EXPERIENCE.COMPANY, entry.company())
              .set(TALENT_EXPERIENCE.POSITION, entry.position())
              .set(TALENT_EXPERIENCE.LOCATION, blankToNull(entry.location()))
              .set(TALENT_EXPERIENCE.WORKPLACE, entry.workplace())
              .set(TALENT_EXPERIENCE.PERIOD, entry.period())
              .set(TALENT_EXPERIENCE.DESCRIPTION, entry.description())
              .execute();
        }
    }

    public void insertEducation(DSLContext tx, long accountId,
                                List<UserOnboardingPayload.Education> education) {
        for (int order = 0; order < size(education); order++) {
            UserOnboardingPayload.Education entry = education.get(order);
            tx.insertInto(TALENT_EDUCATION)
              .set(TALENT_EDUCATION.ACCOUNT_ID, accountId)
              .set(TALENT_EDUCATION.DISPLAY_ORDER, order)
              .set(TALENT_EDUCATION.INSTITUTION, entry.institution())
              .set(TALENT_EDUCATION.DEGREE, entry.degree())
              .set(TALENT_EDUCATION.FIELD, entry.field())
              .set(TALENT_EDUCATION.GRADUATION_YEAR, entry.graduationYear())
              .set(TALENT_EDUCATION.LOCATION, entry.location())
              .execute();
        }
    }

    public void insertCertifications(DSLContext tx, long accountId,
                                     List<UserOnboardingPayload.Certification> certifications) {
        for (int order = 0; order < size(certifications); order++) {
            UserOnboardingPayload.Certification entry = certifications.get(order);
            tx.insertInto(TALENT_CERTIFICATION)
              .set(TALENT_CERTIFICATION.ACCOUNT_ID, accountId)
              .set(TALENT_CERTIFICATION.DISPLAY_ORDER, order)
              .set(TALENT_CERTIFICATION.NAME, entry.name())
              .set(TALENT_CERTIFICATION.ORGANIZATION, entry.organization())
              .set(TALENT_CERTIFICATION.YEAR, entry.year())
              .execute();
        }
    }

    public void insertCourses(DSLContext tx, long accountId,
                              List<UserOnboardingPayload.Course> courses) {
        for (int order = 0; order < size(courses); order++) {
            UserOnboardingPayload.Course entry = courses.get(order);
            tx.insertInto(TALENT_COURSE)
              .set(TALENT_COURSE.ACCOUNT_ID, accountId)
              .set(TALENT_COURSE.DISPLAY_ORDER, order)
              .set(TALENT_COURSE.NAME, entry.name())
              .set(TALENT_COURSE.PROVIDER, entry.provider())
              .set(TALENT_COURSE.FOCUS, entry.focus())
              .set(TALENT_COURSE.COMPLETION_YEAR, entry.completionYear())
              .execute();
        }
    }

    public void insertProjects(DSLContext tx, long accountId,
                               List<UserOnboardingPayload.Project> projects) {
        for (int order = 0; order < size(projects); order++) {
            UserOnboardingPayload.Project entry = projects.get(order);
            tx.insertInto(TALENT_PROJECT)
              .set(TALENT_PROJECT.ACCOUNT_ID, accountId)
              .set(TALENT_PROJECT.DISPLAY_ORDER, order)
              .set(TALENT_PROJECT.TITLE, entry.title())
              .set(TALENT_PROJECT.CATEGORY, entry.category())
              .set(TALENT_PROJECT.YEAR, entry.year())
              .set(TALENT_PROJECT.DESCRIPTION, entry.description())
              .execute();
        }
    }

    public void replaceSkills(DSLContext tx, long accountId, List<String> skills) {
        tx.deleteFrom(TALENT_SKILL).where(TALENT_SKILL.ACCOUNT_ID.eq(accountId)).execute();
        insertSkills(tx, accountId, skills);
    }

    public void replaceLanguages(DSLContext tx, long accountId,
                                 List<UserOnboardingPayload.LanguageEntry> languages) {
        tx.deleteFrom(TALENT_LANGUAGE).where(TALENT_LANGUAGE.ACCOUNT_ID.eq(accountId)).execute();
        insertLanguages(tx, accountId, languages);
    }

    public void replaceExperience(DSLContext tx, long accountId,
                                  List<UserOnboardingPayload.Experience> experience) {
        tx.deleteFrom(TALENT_EXPERIENCE).where(TALENT_EXPERIENCE.ACCOUNT_ID.eq(accountId)).execute();
        insertExperience(tx, accountId, experience);
    }

    public void replaceEducation(DSLContext tx, long accountId,
                                 List<UserOnboardingPayload.Education> education) {
        tx.deleteFrom(TALENT_EDUCATION).where(TALENT_EDUCATION.ACCOUNT_ID.eq(accountId)).execute();
        insertEducation(tx, accountId, education);
    }

    public void replaceCertifications(DSLContext tx, long accountId,
                                      List<UserOnboardingPayload.Certification> certifications) {
        tx.deleteFrom(TALENT_CERTIFICATION).where(TALENT_CERTIFICATION.ACCOUNT_ID.eq(accountId)).execute();
        insertCertifications(tx, accountId, certifications);
    }

    public void replaceCourses(DSLContext tx, long accountId, List<UserOnboardingPayload.Course> courses) {
        tx.deleteFrom(TALENT_COURSE).where(TALENT_COURSE.ACCOUNT_ID.eq(accountId)).execute();
        insertCourses(tx, accountId, courses);
    }

    public void replaceProjects(DSLContext tx, long accountId, List<UserOnboardingPayload.Project> projects) {
        tx.deleteFrom(TALENT_PROJECT).where(TALENT_PROJECT.ACCOUNT_ID.eq(accountId)).execute();
        insertProjects(tx, accountId, projects);
    }

    /**
     * The optional sections arrive absent rather than empty when the wizard had nothing to send, since
     * the front end strips the blank rows it seeds them with.
     */
    private static int size(List<?> entries) {
        return entries == null ? 0 : entries.size();
    }

    /**
     * Nullable references into the location table, and the optional link columns, have to store an empty
     * string as NULL - for a reference, an empty string is a foreign key nothing matches.
     */
    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
