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

import static ro.prospero.aidir.jooq.generated.public_.Tables.ACCOUNT;
import static ro.prospero.aidir.jooq.generated.public_.Tables.PLAN;
import static ro.prospero.aidir.jooq.generated.public_.Tables.PLAN_PRICE;
import static ro.prospero.aidir.jooq.generated.public_.Tables.TALENT_PROFILE;

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
    private final TalentProfileSections sections;

    public UserOnboardingService(DSLContext dslContext,
                                 SubscriptionService subscriptionService,
                                 PasswordEncoder passwordEncoder,
                                 TalentProfileSections sections) {
        this.dslContext = dslContext;
        this.subscriptionService = subscriptionService;
        this.passwordEncoder = passwordEncoder;
        this.sections = sections;
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
                           .set(ACCOUNT.EMAIL, AccountService.normalizeEmail(basic.email()))
                           .set(ACCOUNT.PASSWORD, passwordEncoder.encode(basic.password()))
                           .set(ACCOUNT.ACCOUNT_TYPE, TALENT)
                           .set(ACCOUNT.DISPLAY_NAME, basic.fullName())
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

        sections.insertSkills(tx, accountId, profile.skills());
        sections.insertLanguages(tx, accountId, profile.languages());
        sections.insertExperience(tx, accountId, profile.experience());
        sections.insertEducation(tx, accountId, profile.education());
        sections.insertCertifications(tx, accountId, profile.certifications());
        sections.insertCourses(tx, accountId, profile.courses());
        sections.insertProjects(tx, accountId, profile.projects());
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
}
