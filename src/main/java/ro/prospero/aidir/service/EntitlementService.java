package ro.prospero.aidir.service;

import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import ro.prospero.aidir.jooq.generated.public_.tables.FeatureGrant;

import java.time.OffsetDateTime;

import static ro.prospero.aidir.jooq.generated.public_.Tables.ACCOUNT_ADDON;
import static ro.prospero.aidir.jooq.generated.public_.Tables.ADDON;
import static ro.prospero.aidir.jooq.generated.public_.Tables.ADDON_FEATURE;
import static ro.prospero.aidir.jooq.generated.public_.Tables.FEATURE;
import static ro.prospero.aidir.jooq.generated.public_.Tables.FEATURE_GRANT;
import static ro.prospero.aidir.jooq.generated.public_.Tables.PLAN_FEATURE;
import static ro.prospero.aidir.jooq.generated.public_.Tables.SUBSCRIPTION;

/**
 * Everything that reads or writes feature_grant.
 *
 * <p>A numeric entitlement is the sum of an account's live grants, and nothing else: no plan
 * lookup, no add-on union, no subscription status. Boolean features are not granted at all -
 * nothing consumes them, so they are read straight off the covering period's plan and off live
 * add-ons rather than minting dozens of untouched rows every period.
 *
 * <p>The mint/carry/clamp trio is what keeps that true across a subscription's life, and it is
 * driven by {@link SubscriptionService} rather than called directly. Each takes a jOOQ
 * {@link Configuration} so it runs inside that caller's transaction.
 */
@Service
public class EntitlementService {

    /** feature_kind values that produce grant rows. Boolean features never do. */
    private static final String KIND_QUOTA = "quota";
    private static final String KIND_CONSUMABLE = "consumable";

    /** A spend that loses a race retries, but only against a balance that says it can afford to. */
    private static final int CONSUME_ATTEMPTS = 3;

    private final DSLContext dslContext;

    public EntitlementService(DSLContext dslContext) {
        this.dslContext = dslContext;
    }

    // -----------------------------------------------------------------------
    // Reads
    // -----------------------------------------------------------------------

    /**
     * How many uses of a numeric feature the account can still spend. This is the entire
     * resolver - a grant is live if it has something left and has not expired, and a grace
     * period's grants are live on exactly the same terms as any other period's.
     */
    public int balance(long accountId, String featureCode) {
        OffsetDateTime now = OffsetDateTime.now();
        Integer total = dslContext
                .select(DSL.sum(FEATURE_GRANT.REMAINING))
                .from(FEATURE_GRANT)
                .where(FEATURE_GRANT.ACCOUNT_ID.eq(accountId))
                .and(FEATURE_GRANT.FEATURE_CODE.eq(featureCode))
                .and(FEATURE_GRANT.REMAINING.gt(0))
                .and(FEATURE_GRANT.EXPIRES_AT.isNull().or(FEATURE_GRANT.EXPIRES_AT.gt(now)))
                .fetchOne(0, Integer.class);
        return total == null ? 0 : total;
    }

    /**
     * Whether a boolean feature is switched on, from the plan of the period covering now or from
     * a live add-on. Neither half filters on a status: a period entitles for exactly as long as
     * its own range says it does.
     */
    public boolean hasFeature(long accountId, String featureCode) {
        OffsetDateTime now = OffsetDateTime.now();

        boolean fromPlan = dslContext.fetchExists(
                DSL.selectOne()
                   .from(SUBSCRIPTION)
                   .join(PLAN_FEATURE).on(PLAN_FEATURE.PLAN_ID.eq(SUBSCRIPTION.PLAN_ID))
                   .where(SUBSCRIPTION.ACCOUNT_ID.eq(accountId))
                   .and(PLAN_FEATURE.FEATURE_CODE.eq(featureCode))
                   .and(SUBSCRIPTION.PERIOD_START.le(now))
                   .and(SUBSCRIPTION.PERIOD_END.gt(now)));
        if (fromPlan) {
            return true;
        }

        return dslContext.fetchExists(
                DSL.selectOne()
                   .from(ACCOUNT_ADDON)
                   .join(ADDON_FEATURE).on(ADDON_FEATURE.ADDON_ID.eq(ACCOUNT_ADDON.ADDON_ID))
                   .where(ACCOUNT_ADDON.ACCOUNT_ID.eq(accountId))
                   .and(ADDON_FEATURE.FEATURE_CODE.eq(featureCode))
                   .and(ACCOUNT_ADDON.CANCELLED_AT.isNull())
                   .and(ACCOUNT_ADDON.STARTS_AT.le(now))
                   .and(ACCOUNT_ADDON.ENDS_AT.isNull().or(ACCOUNT_ADDON.ENDS_AT.gt(now))));
    }

    // -----------------------------------------------------------------------
    // Spending
    // -----------------------------------------------------------------------

    /**
     * Spend {@code n} uses of a numeric feature, soonest-expiring grant first so the non-expiring
     * ones stay banked for as long as possible. Returns false when the account cannot afford it.
     *
     * <p>One statement, so it is atomic without a surrounding transaction. The subquery locks the
     * grant it picks; the outer {@code remaining >= n} is re-evaluated after that lock is granted,
     * so a concurrent spender cannot push the balance negative even if the lock is somehow not
     * taken. Losing that race matches zero rows while a different grant may still be affordable,
     * hence the retry.
     */
    public boolean consume(long accountId, String featureCode, int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("Cannot spend " + n + " of " + featureCode);
        }
        for (int attempt = 0; attempt < CONSUME_ATTEMPTS; attempt++) {
            if (spendOnce(accountId, featureCode, n)) {
                return true;
            }
            if (balance(accountId, featureCode) < n) {
                return false;
            }
        }
        return false;
    }

    private boolean spendOnce(long accountId, String featureCode, int n) {
        OffsetDateTime now = OffsetDateTime.now();
        // Aliased so the row being picked is plainly a different scope from the row being
        // updated, rather than relying on the inner FROM shadowing the UPDATE target.
        FeatureGrant pick = FEATURE_GRANT.as("pick");

        return dslContext.update(FEATURE_GRANT)
                .set(FEATURE_GRANT.REMAINING, FEATURE_GRANT.REMAINING.minus(n))
                .where(FEATURE_GRANT.ID.eq(
                        DSL.select(pick.ID)
                           .from(pick)
                           .where(pick.ACCOUNT_ID.eq(accountId))
                           .and(pick.FEATURE_CODE.eq(featureCode))
                           .and(pick.REMAINING.ge(n))
                           .and(pick.EXPIRES_AT.isNull().or(pick.EXPIRES_AT.gt(now)))
                           // NULLS LAST: a grant that never expires is spent only once the
                           // dated ones are gone.
                           .orderBy(pick.EXPIRES_AT.asc().nullsLast(), pick.ID.asc())
                           .limit(1)
                           .forUpdate()))
                .and(FEATURE_GRANT.REMAINING.ge(n))
                .execute() == 1;
    }

    // -----------------------------------------------------------------------
    // Minting, carrying, clamping - driven by SubscriptionService
    // -----------------------------------------------------------------------

    /**
     * Mint a period's quota grants: one row per quota feature the plan carries, plus one per
     * quota feature a live recurring add-on carries, all expiring when the period does.
     *
     * <p>Called for every period that opens, grace included - that is what makes a grace period
     * behave like any other beginning. Consumables are not minted here; they come from a purchase
     * and outlive every period.
     */
    void mintPeriodGrants(Configuration cfg, long subscriptionId, long accountId,
                          long planId, OffsetDateTime expiresAt) {
        DSLContext tx = DSL.using(cfg);
        OffsetDateTime now = OffsetDateTime.now();

        tx.insertInto(FEATURE_GRANT,
                      FEATURE_GRANT.ACCOUNT_ID, FEATURE_GRANT.FEATURE_CODE,
                      FEATURE_GRANT.AMOUNT, FEATURE_GRANT.REMAINING,
                      FEATURE_GRANT.EXPIRES_AT, FEATURE_GRANT.SUBSCRIPTION_ID)
          .select(DSL.select(DSL.val(accountId), PLAN_FEATURE.FEATURE_CODE,
                             PLAN_FEATURE.QUOTA, PLAN_FEATURE.QUOTA,
                             DSL.val(expiresAt), DSL.val(subscriptionId))
                     .from(PLAN_FEATURE)
                     .join(FEATURE).on(FEATURE.CODE.eq(PLAN_FEATURE.FEATURE_CODE))
                     .where(PLAN_FEATURE.PLAN_ID.eq(planId))
                     .and(FEATURE.KIND.eq(KIND_QUOTA))
                     .and(PLAN_FEATURE.QUOTA.gt(0)))
          .execute();

        tx.insertInto(FEATURE_GRANT,
                      FEATURE_GRANT.ACCOUNT_ID, FEATURE_GRANT.FEATURE_CODE,
                      FEATURE_GRANT.AMOUNT, FEATURE_GRANT.REMAINING,
                      FEATURE_GRANT.EXPIRES_AT, FEATURE_GRANT.SUBSCRIPTION_ID,
                      FEATURE_GRANT.ACCOUNT_ADDON_ID)
          .select(DSL.select(DSL.val(accountId), ADDON_FEATURE.FEATURE_CODE,
                             ADDON_FEATURE.QUANTITY, ADDON_FEATURE.QUANTITY,
                             DSL.val(expiresAt), DSL.val(subscriptionId), ACCOUNT_ADDON.ID)
                     .from(ACCOUNT_ADDON)
                     .join(ADDON).on(ADDON.ID.eq(ACCOUNT_ADDON.ADDON_ID))
                     .join(ADDON_FEATURE).on(ADDON_FEATURE.ADDON_ID.eq(ACCOUNT_ADDON.ADDON_ID))
                     .join(FEATURE).on(FEATURE.CODE.eq(ADDON_FEATURE.FEATURE_CODE))
                     .where(ACCOUNT_ADDON.ACCOUNT_ID.eq(accountId))
                     .and(ADDON.BILLING.eq("recurring"))
                     .and(FEATURE.KIND.eq(KIND_QUOTA))
                     .and(ADDON_FEATURE.QUANTITY.gt(0))
                     .and(ACCOUNT_ADDON.CANCELLED_AT.isNull())
                     .and(ACCOUNT_ADDON.STARTS_AT.le(now))
                     .and(ACCOUNT_ADDON.ENDS_AT.isNull().or(ACCOUNT_ADDON.ENDS_AT.gt(now))))
          .execute();
    }

    /**
     * Move a period's grants to its successor and push their deadline out, minting nothing.
     *
     * <p>This is the one place an allowance survives a period boundary, and it exists for exactly
     * one caller: payment arriving during grace. Refreshing there instead would let an account
     * spend a grace allowance and then buy a second one for the same month.
     */
    void carryGrants(Configuration cfg, long fromSubscriptionId, long toSubscriptionId,
                     OffsetDateTime newExpiry) {
        DSL.using(cfg)
           .update(FEATURE_GRANT)
           .set(FEATURE_GRANT.SUBSCRIPTION_ID, toSubscriptionId)
           .set(FEATURE_GRANT.EXPIRES_AT, newExpiry)
           .where(FEATURE_GRANT.SUBSCRIPTION_ID.eq(fromSubscriptionId))
           // Consumables ride on the account, not on a period, and are left alone.
           .and(FEATURE_GRANT.EXPIRES_AT.isNotNull())
           .execute();
    }

    /**
     * Pull a period's grants back to the instant it was truncated, so that a period's allowance
     * lives exactly as long as the period did.
     */
    void clampGrants(Configuration cfg, long subscriptionId, OffsetDateTime at) {
        DSL.using(cfg)
           .update(FEATURE_GRANT)
           .set(FEATURE_GRANT.EXPIRES_AT, at)
           .where(FEATURE_GRANT.SUBSCRIPTION_ID.eq(subscriptionId))
           .and(FEATURE_GRANT.EXPIRES_AT.isNotNull())
           .and(FEATURE_GRANT.EXPIRES_AT.gt(at))
           .execute();
    }

    /**
     * Grant what an add-on purchase carries, right away rather than at the next rollover - a
     * recurring pack bought mid-period has been paid for and has to be spendable now.
     *
     * <p>The two kinds expire on different clocks, which is why this takes two deadlines. A
     * consumable rides on the add-on's own window, usually forever; a quota is a per-period
     * allowance, so it expires with the period the purchase landed in and the next
     * {@link #mintPeriodGrants} replaces it rather than stacking on top of it. Boolean-only
     * add-ons produce no grant at all, their time-boxing already being account_addon.ends_at.
     */
    void grantAddonFeatures(Configuration cfg, long accountAddonId, long accountId, long addonId,
                            OffsetDateTime consumableExpiry, OffsetDateTime quotaExpiry) {
        // Both binds are routinely null, so they have to carry their type with them.
        Field<OffsetDateTime> expiry =
                DSL.when(FEATURE.KIND.eq(KIND_CONSUMABLE),
                         DSL.val(consumableExpiry, FEATURE_GRANT.EXPIRES_AT.getDataType()))
                   .otherwise(DSL.val(quotaExpiry, FEATURE_GRANT.EXPIRES_AT.getDataType()));

        DSL.using(cfg)
           .insertInto(FEATURE_GRANT,
                       FEATURE_GRANT.ACCOUNT_ID, FEATURE_GRANT.FEATURE_CODE,
                       FEATURE_GRANT.AMOUNT, FEATURE_GRANT.REMAINING,
                       FEATURE_GRANT.EXPIRES_AT, FEATURE_GRANT.ACCOUNT_ADDON_ID)
           .select(DSL.select(DSL.val(accountId), ADDON_FEATURE.FEATURE_CODE,
                              ADDON_FEATURE.QUANTITY, ADDON_FEATURE.QUANTITY,
                              expiry, DSL.val(accountAddonId))
                      .from(ADDON_FEATURE)
                      .join(FEATURE).on(FEATURE.CODE.eq(ADDON_FEATURE.FEATURE_CODE))
                      .where(ADDON_FEATURE.ADDON_ID.eq(addonId))
                      .and(FEATURE.KIND.in(KIND_CONSUMABLE, KIND_QUOTA))
                      .and(ADDON_FEATURE.QUANTITY.gt(0)))
           .execute();
    }
}
