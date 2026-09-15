package ro.prospero.aidir.service;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ro.prospero.aidir.data.FreeUserOnboardingPayload;
import ro.prospero.aidir.data.UserBasic;
import ro.prospero.aidir.data.UserOnboardingPayload;
import ro.prospero.aidir.data.UserOnboardingPayload.Profile;
import ro.prospero.aidir.data.UserOnboardingResult;
import ro.prospero.aidir.data.UserPlanKey;

import java.time.OffsetDateTime;
import java.util.List;

import static ro.prospero.aidir.jooq.generated.public_.Tables.ACCOUNT;
import static ro.prospero.aidir.jooq.generated.public_.Tables.PLAN;
import static ro.prospero.aidir.jooq.generated.public_.Tables.PLAN_PRICE;
import static ro.prospero.aidir.jooq.generated.public_.Tables.TALENT_CERTIFICATION;
import static ro.prospero.aidir.jooq.generated.public_.Tables.TALENT_COURSE;
import static ro.prospero.aidir.jooq.generated.public_.Tables.TALENT_EDUCATION;
import static ro.prospero.aidir.jooq.generated.public_.Tables.TALENT_EXPERIENCE;
import static ro.prospero.aidir.jooq.generated.public_.Tables.TALENT_LANGUAGE;
import static ro.prospero.aidir.jooq.generated.public_.Tables.TALENT_PROFILE;
import static ro.prospero.aidir.jooq.generated.public_.Tables.TALENT_PROJECT;
import static ro.prospero.aidir.jooq.generated.public_.Tables.TALENT_SKILL;

/**
 * The talent wizard's one write, in two shapes: a free signup is an account and the subscription that
 * comes with it, a paid one is that plus the profile and the seven sections hanging off it.
 *
 * <p>Account and profile are one transaction because talent who cannot sign in and a profile with no
 * owner are both worse than a failed signup. Transactions come from jOOQ rather than
 * {@code @Transactional}: JooqConfig builds its DSLContext over the raw DataSource, so Spring's
 * transaction manager would not enrol these statements (the same reason {@link SubscriptionService}
 * works this way).
 */
@Service
public class UserOnboardingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserOnboardingService.class);

    private static final String TALENT = "talent";
    private static final String YEARLY = "yearly";
    private static final String CURRENCY = "EUR";

    private final DSLContext dslContext;
    private final SubscriptionService subscriptionService;
    private final PasswordEncoder passwordEncoder;

    public UserOnboardingService(DSLContext dslContext,
                                 SubscriptionService subscriptionService,
                                 PasswordEncoder passwordEncoder) {
        this.dslContext = dslContext;
        this.subscriptionService = subscriptionService;
        this.passwordEncoder = passwordEncoder;
    }

    public UserOnboardingResult onboardFree(FreeUserOnboardingPayload payload) {
        long accountId = dslContext.transactionResult(cfg -> insertAccount(DSL.using(cfg), payload.basic()));

        openSubscription(accountId, UserPlanKey.FREE);
        return new UserOnboardingResult(accountId);
    }

    public UserOnboardingResult onboard(UserOnboardingPayload payload) {
        long accountId = dslContext.transactionResult(cfg -> {
            DSLContext tx = DSL.using(cfg);
            long id = insertAccount(tx, payload.basic());
            insertProfile(tx, id, payload);
            return id;
        });

        //fixme: this should not have to live outside of the transaction just because the logic exists in a different class
        openSubscription(accountId, payload.planKey());
        return new UserOnboardingResult(accountId);
    }

    private long insertAccount(DSLContext tx, UserBasic basic) {
        Long accountId = tx.insertInto(ACCOUNT)
                           .set(ACCOUNT.EMAIL, basic.email())
                           .set(ACCOUNT.PASSWORD, passwordEncoder.encode(basic.password()))
                           .set(ACCOUNT.ACCOUNT_TYPE, TALENT)
                           .set(ACCOUNT.TERMS_ACCEPTED_AT, OffsetDateTime.now())
                           .returningResult(ACCOUNT.ID)
                           .fetchOne(ACCOUNT.ID);
        if (accountId == null) {
            throw new IllegalStateException("Creating the talent account returned no id");
        }
        return accountId;
    }

    /**
     * The name and country are the account's, collected a step earlier than the rest of the profile -
     * they are on the talent_profile row because that is what the directory reads.
     */
    private void insertProfile(DSLContext tx, long accountId, UserOnboardingPayload payload) {
        Profile profile = payload.profile();
        UserOnboardingPayload.WorkPreferences work = profile.work();
        UserOnboardingPayload.ProfileLinks links = profile.links();

        tx.insertInto(TALENT_PROFILE)
          .set(TALENT_PROFILE.ACCOUNT_ID, accountId)
          .set(TALENT_PROFILE.FULL_NAME, payload.basic().fullName())
          .set(TALENT_PROFILE.COUNTRY, payload.basic().country())
          .set(TALENT_PROFILE.TITLE, profile.title())
          .set(TALENT_PROFILE.SHORT_DESCRIPTION, profile.shortDescription())
          .set(TALENT_PROFILE.SUMMARY, profile.summary())
          .set(TALENT_PROFILE.WORK_LOCATION, work.location())
          .set(TALENT_PROFILE.WORK_RELOCATION, work.relocation())
          .set(TALENT_PROFILE.WORK_WORKPLACE, work.workplace())
          .set(TALENT_PROFILE.WORK_EMPLOYMENT_TYPE, work.employmentType())
          .set(TALENT_PROFILE.LINK_LINKEDIN, links == null ? null : links.linkedin())
          .set(TALENT_PROFILE.LINK_GITHUB, links == null ? null : links.github())
          .set(TALENT_PROFILE.LINK_PORTFOLIO, links == null ? null : links.portfolio())
          .set(TALENT_PROFILE.LINK_OTHER, links == null ? null : links.other())
          .execute();

        insertSkills(tx, accountId, profile.skills());
        insertLanguages(tx, accountId, profile.languages());
        insertExperience(tx, accountId, profile.experience());
        insertEducation(tx, accountId, profile.education());
        insertCertifications(tx, accountId, profile.certifications());
        insertCourses(tx, accountId, profile.courses());
        insertProjects(tx, accountId, profile.projects());
    }

    private void insertSkills(DSLContext tx, long accountId, List<String> skills) {
        for (String skill : skills) {
            tx.insertInto(TALENT_SKILL)
              .set(TALENT_SKILL.ACCOUNT_ID, accountId)
              .set(TALENT_SKILL.SKILL, skill)
              .execute();
        }
    }

    private void insertLanguages(DSLContext tx, long accountId,
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

    private void insertExperience(DSLContext tx, long accountId,
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

    private void insertEducation(DSLContext tx, long accountId,
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

    private void insertCertifications(DSLContext tx, long accountId,
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

    private void insertCourses(DSLContext tx, long accountId,
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

    private void insertProjects(DSLContext tx, long accountId,
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

    /**
     * Outside the transaction above, and it has to be: {@link SubscriptionService} opens its own on the
     * shared DSLContext, so it runs on another connection and would not see an account that is not
     * committed yet - the subscription's foreign key would fail.
     */
    private void openSubscription(long accountId, UserPlanKey planKey) {
        String planName = planKey.wire();
        var price = dslContext.select(PLAN.ID, PLAN_PRICE.AMOUNT_CENTS)
                              .from(PLAN)
                              .join(PLAN_PRICE).on(PLAN_PRICE.PLAN_ID.eq(PLAN.ID))
                              .where(PLAN.ACCOUNT_TYPE.eq(TALENT))
                              .and(PLAN.NAME.eq(planName))
                              .and(PLAN_PRICE.RECURRENCE.eq(YEARLY))
                              .fetchOne();
        if (price == null) {
            throw new IllegalStateException("No yearly price for the talent plan '" + planName + "'");
        }

        // Nothing has been charged: there is no payment provider yet, so a paid plan's first period opens
        // unpaid and stays that way until the dunning work in TASKS.adoc lands.
        subscriptionService.create(accountId, price.value1(), YEARLY, price.value2(), CURRENCY);
        LOGGER.info("Opened the first {} period for account {} on the talent '{}' plan, unpaid",
                    YEARLY, accountId, planName);
    }

    /**
     * The optional sections arrive absent rather than empty when the wizard had nothing to send, since
     * the front end strips the blank rows it seeds them with.
     */
    private static int size(List<?> entries) {
        return entries == null ? 0 : entries.size();
    }

    /**
     * An experience row's location is the one nullable reference into the location table, so an empty
     * string has to become a NULL rather than a foreign key nothing matches.
     */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
