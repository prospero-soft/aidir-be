package ro.prospero.aidir.service;

import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import ro.prospero.aidir.data.SubscriptionEnd;
import ro.prospero.aidir.data.SubscriptionReason;
import ro.prospero.aidir.jooq.generated.public_.tables.records.SubscriptionRecord;

import java.time.OffsetDateTime;
import java.util.Optional;

import static ro.prospero.aidir.jooq.generated.public_.Tables.ACCOUNT;
import static ro.prospero.aidir.jooq.generated.public_.Tables.ACCOUNT_ADDON;
import static ro.prospero.aidir.jooq.generated.public_.Tables.ADDON;
import static ro.prospero.aidir.jooq.generated.public_.Tables.PLAN;
import static ro.prospero.aidir.jooq.generated.public_.Tables.SUBSCRIPTION;

/**
 * An account's subscription history, which is the whole of its standing: one row per period,
 * non-overlapping, and no state held anywhere else.
 *
 * <p>Every lifecycle event on this class is a composition of two primitives - {@link #closePeriod}
 * to cut a period short and {@link #openPeriod} to begin one. A grace period is not special: it is
 * an ordinary period on the same plan that happens to charge nothing and end sooner. The single
 * deliberate exception in the system is that recovering from grace carries the grace period's
 * allowance forward rather than minting a new one, and that is expressed by passing
 * {@link GrantPolicy.CarryFrom} rather than by a branch inside either primitive.
 *
 * <p>Transactions come from jOOQ rather than {@code @Transactional}: JooqConfig builds its
 * DSLContext over the raw DataSource, so Spring's transaction manager would not enrol these
 * statements. Truncating and re-opening must be atomic, both because the exclusion constraint
 * rejects the successor until the predecessor is out of the way, and because a crash between the
 * two would leave the account with no period covering now.
 */
@Service
public class SubscriptionService {

    /**
     * period_end for the free plans, which never lapse. PgJDBC maps OffsetDateTime.MAX to
     * Postgres 'infinity' and back.
     */
    public static final OffsetDateTime FOREVER = OffsetDateTime.MAX;

    private static final String FREE_PLAN = "free";
    private static final String MONTHLY = "monthly";
    private static final String YEARLY = "yearly";

    private final DSLContext dslContext;
    private final EntitlementService entitlements;

    public SubscriptionService(DSLContext dslContext, EntitlementService entitlements) {
        this.dslContext = dslContext;
        this.entitlements = entitlements;
    }

    // -----------------------------------------------------------------------
    // Reads
    // -----------------------------------------------------------------------

    /** The period covering now, if there is one. No status filter: the range is the answer. */
    public Optional<SubscriptionRecord> current(long accountId) {
        return current(dslContext, accountId);
    }

    private static Optional<SubscriptionRecord> current(DSLContext tx, long accountId) {
        OffsetDateTime now = OffsetDateTime.now();
        return tx.selectFrom(SUBSCRIPTION)
                 .where(SUBSCRIPTION.ACCOUNT_ID.eq(accountId))
                 .and(SUBSCRIPTION.PERIOD_START.le(now))
                 .and(SUBSCRIPTION.PERIOD_END.gt(now))
                 .fetchOptional();
    }

    /** Whether the account is currently inside a grace period. */
    public boolean inGrace(long accountId) {
        return current(accountId)
                .map(s -> SubscriptionReason.PAYMENT_FAILED.wire().equals(s.getReason()))
                .orElse(false);
    }

    // -----------------------------------------------------------------------
    // Primitives
    // -----------------------------------------------------------------------

    /**
     * Begin a period and settle its allowance. The single path for every beginning: signup,
     * trial, renewal, upgrade, downgrade, demotion, and grace.
     *
     * @return the new subscription row's id
     */
    public long openPeriod(long accountId, long planId, String recurrence,
                           OffsetDateTime start, OffsetDateTime end,
                           SubscriptionReason reason,
                           Integer amountCents, String currency,
                           GrantPolicy grants) {
        return dslContext.transactionResult(cfg -> openPeriod(cfg, accountId, planId, recurrence,
                                                              start, end, reason,
                                                              amountCents, currency, grants));
    }

    private long openPeriod(Configuration cfg, long accountId, long planId, String recurrence,
                            OffsetDateTime start, OffsetDateTime end,
                            SubscriptionReason reason,
                            Integer amountCents, String currency,
                            GrantPolicy grants) {
        Long subscriptionId = DSL.using(cfg)
                .insertInto(SUBSCRIPTION)
                .set(SUBSCRIPTION.ACCOUNT_ID, accountId)
                .set(SUBSCRIPTION.PLAN_ID, planId)
                .set(SUBSCRIPTION.RECURRENCE, recurrence)
                .set(SUBSCRIPTION.REASON, reason.wire())
                .set(SUBSCRIPTION.PERIOD_START, start)
                .set(SUBSCRIPTION.PERIOD_END, end)
                .set(SUBSCRIPTION.AMOUNT_CENTS, amountCents)
                .set(SUBSCRIPTION.CURRENCY, currency)
                .returningResult(SUBSCRIPTION.ID)
                .fetchOne(SUBSCRIPTION.ID);
        if (subscriptionId == null) {
            throw new IllegalStateException("Opening a period for account " + accountId
                                            + " returned no id");
        }

        switch (grants) {
            case GrantPolicy.Mint ignored ->
                    entitlements.mintPeriodGrants(cfg, subscriptionId, accountId, planId, end);
            case GrantPolicy.CarryFrom carry ->
                    entitlements.carryGrants(cfg, carry.subscriptionId(), subscriptionId, end);
        }
        return subscriptionId;
    }

    /**
     * Cut a period short, pulling its allowance back to the same instant so that a period's
     * grants live exactly as long as the period did. Run this before opening the successor: the
     * exclusion constraint will not admit an overlapping range.
     */
    public void closePeriod(long subscriptionId, OffsetDateTime at, SubscriptionEnd endedAs) {
        dslContext.transaction(cfg -> closePeriod(cfg, subscriptionId, at, endedAs));
    }

    private void closePeriod(Configuration cfg, long subscriptionId, OffsetDateTime at,
                             SubscriptionEnd endedAs) {
        DSL.using(cfg)
           .update(SUBSCRIPTION)
           .set(SUBSCRIPTION.PERIOD_END, at)
           .set(SUBSCRIPTION.ENDED_AS, endedAs.wire())
           .where(SUBSCRIPTION.ID.eq(subscriptionId))
           .execute();

        entitlements.clampGrants(cfg, subscriptionId, at);
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    /** An account's first period. */
    public long create(long accountId, long planId, String recurrence,
                       Integer amountCents, String currency) {
        OffsetDateTime now = OffsetDateTime.now();
        return openPeriod(accountId, planId, recurrence, now, advance(now, recurrence),
                          SubscriptionReason.CREATED, amountCents, currency, GrantPolicy.MINT);
    }

    /**
     * The renewal charge went through. The new period picks up where the old one ended rather
     * than at now, so the periods stay contiguous however late the job runs.
     */
    public long renew(SubscriptionRecord expired, Integer amountCents, String currency) {
        OffsetDateTime start = expired.getPeriodEnd();
        return openPeriod(expired.getAccountId(), expired.getPlanId(), expired.getRecurrence(),
                          start, advance(start, expired.getRecurrence()),
                          SubscriptionReason.RENEWED, amountCents, currency, GrantPolicy.MINT);
    }

    /**
     * The renewal charge failed. This opens an ordinary period on the same plan that charges
     * nothing and runs for the plan's grace window - so the allowance refreshes and then lapses
     * with the window, exactly as a paid period's would.
     */
    public long beginGrace(SubscriptionRecord expired) {
        return dslContext.transactionResult(cfg -> {
            int graceDays = DSL.using(cfg)
                    .select(PLAN.GRACE_DAYS)
                    .from(PLAN)
                    .where(PLAN.ID.eq(expired.getPlanId()))
                    .fetchOne(PLAN.GRACE_DAYS);

            OffsetDateTime start = expired.getPeriodEnd();
            return openPeriod(cfg, expired.getAccountId(), expired.getPlanId(),
                              expired.getRecurrence(), start, start.plusDays(graceDays),
                              SubscriptionReason.PAYMENT_FAILED, null, null, GrantPolicy.MINT);
        });
    }

    /**
     * Payment landed during grace. The paid period runs to the end the period would have had if
     * the charge had gone through first time, and it inherits whatever the grace period had left
     * rather than a fresh allowance - otherwise an account could spend a grace allowance and then
     * buy a second one for the same month.
     */
    public long recoverFromGrace(SubscriptionRecord grace, Integer amountCents, String currency) {
        OffsetDateTime now = OffsetDateTime.now();
        // Grace began where the paid period ended, so this is that period's regular end.
        OffsetDateTime regularEnd = advance(grace.getPeriodStart(), grace.getRecurrence());

        return dslContext.transactionResult(cfg -> {
            closePeriod(cfg, grace.getId(), now, SubscriptionEnd.SUPERSEDED);
            return openPeriod(cfg, grace.getAccountId(), grace.getPlanId(), grace.getRecurrence(),
                              now, regularEnd, SubscriptionReason.REACTIVATED,
                              amountCents, currency, GrantPolicy.carryFrom(grace.getId()));
        });
    }

    /**
     * Grace ran out unpaid. No truncation - the grace row ended on its own, and the free period
     * picks up from where it stopped so there is no gap to fall through.
     */
    public long demoteAfterGrace(SubscriptionRecord grace) {
        return dslContext.transactionResult(cfg -> {
            long freePlanId = freePlanIdFor(cfg, grace.getAccountId());
            return openPeriod(cfg, grace.getAccountId(), freePlanId, YEARLY,
                              grace.getPeriodEnd(), FOREVER,
                              SubscriptionReason.DEMOTED, null, null, GrantPolicy.MINT);
        });
    }

    /**
     * Move to another plan now. Whatever the outgoing period had left expires with it, and the
     * incoming plan mints its own.
     */
    public long changePlan(SubscriptionRecord currentPeriod, long newPlanId, String recurrence,
                           SubscriptionReason reason, Integer amountCents, String currency) {
        if (reason != SubscriptionReason.UPGRADED && reason != SubscriptionReason.DOWNGRADED) {
            throw new IllegalArgumentException("A plan change is an upgrade or a downgrade, not "
                                               + reason);
        }
        OffsetDateTime now = OffsetDateTime.now();
        return dslContext.transactionResult(cfg -> {
            closePeriod(cfg, currentPeriod.getId(), now, SubscriptionEnd.SUPERSEDED);
            return openPeriod(cfg, currentPeriod.getAccountId(), newPlanId, recurrence,
                              now, advance(now, recurrence), reason,
                              amountCents, currency, GrantPolicy.MINT);
        });
    }

    /**
     * Buy an add-on. The purchase row is the commerce record; whatever the add-on grants stands on
     * its own feature_grant rows from here, so cancelling later stops the renewal without clawing
     * back what was already granted.
     *
     * <p>An add-on with no duration grants for good - that is what a consumable is - while a dated
     * one expires on its own schedule rather than with the subscription period. Recurring add-ons
     * additionally top up each period, which openPeriod handles when it mints.
     *
     * @return the new account_addon row's id
     */
    public long purchaseAddon(long accountId, long addonId) {
        return dslContext.transactionResult(cfg -> {
            DSLContext tx = DSL.using(cfg);
            Integer durationDays = tx.select(ADDON.DEFAULT_DURATION_DAYS)
                                     .from(ADDON)
                                     .where(ADDON.ID.eq(addonId))
                                     .fetchOne(ADDON.DEFAULT_DURATION_DAYS);

            OffsetDateTime now = OffsetDateTime.now();
            Long accountAddonId = tx.insertInto(ACCOUNT_ADDON)
                    .set(ACCOUNT_ADDON.ACCOUNT_ID, accountId)
                    .set(ACCOUNT_ADDON.ADDON_ID, addonId)
                    .set(ACCOUNT_ADDON.STARTS_AT, now)
                    .set(ACCOUNT_ADDON.ENDS_AT,
                         durationDays == null ? null : now.plusDays(durationDays))
                    .returningResult(ACCOUNT_ADDON.ID)
                    .fetchOne(ACCOUNT_ADDON.ID);
            if (accountAddonId == null) {
                throw new IllegalStateException("Purchasing add-on " + addonId + " for account "
                                                + accountId + " returned no id");
            }

            // A consumable rides on the add-on's own window; a quota is a per-period allowance and
            // expires with the period this purchase landed in, which the next mint then replaces.
            OffsetDateTime consumableExpiry =
                    durationDays == null ? null : now.plusDays(durationDays);
            OffsetDateTime quotaExpiry = current(tx, accountId)
                    .map(SubscriptionRecord::getPeriodEnd)
                    .orElse(consumableExpiry);

            entitlements.grantAddonFeatures(cfg, accountAddonId, accountId, addonId,
                                            consumableExpiry, quotaExpiry);
            return accountAddonId;
        });
    }

    /** Stop renewing but let the paid-for period run out. Nothing changes until it does. */
    public void cancelAtPeriodEnd(long subscriptionId) {
        dslContext.update(SUBSCRIPTION)
                  .set(SUBSCRIPTION.CANCEL_AT_PERIOD_END, true)
                  .where(SUBSCRIPTION.ID.eq(subscriptionId))
                  .execute();
    }

    /** Drop to free now, forfeiting the rest of the period and its allowance. */
    public long cancelImmediately(SubscriptionRecord currentPeriod) {
        OffsetDateTime now = OffsetDateTime.now();
        return dslContext.transactionResult(cfg -> {
            closePeriod(cfg, currentPeriod.getId(), now, SubscriptionEnd.CANCELLED);
            long freePlanId = freePlanIdFor(cfg, currentPeriod.getAccountId());
            return openPeriod(cfg, currentPeriod.getAccountId(), freePlanId, YEARLY,
                              now, FOREVER, SubscriptionReason.DEMOTED, null, null,
                              GrantPolicy.MINT);
        });
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** A vendor 'free' is not a talent 'free', so this goes through the account's type. */
    private long freePlanIdFor(Configuration cfg, long accountId) {
        Long planId = DSL.using(cfg)
                .select(PLAN.ID)
                .from(PLAN)
                .join(ACCOUNT).on(ACCOUNT.ACCOUNT_TYPE.eq(PLAN.ACCOUNT_TYPE))
                .where(ACCOUNT.ID.eq(accountId))
                .and(PLAN.NAME.eq(FREE_PLAN))
                .fetchOne(PLAN.ID);
        if (planId == null) {
            throw new IllegalStateException("No free plan for the type of account " + accountId);
        }
        return planId;
    }

    private static OffsetDateTime advance(OffsetDateTime from, String recurrence) {
        return switch (recurrence) {
            case MONTHLY -> from.plusMonths(1);
            case YEARLY -> from.plusYears(1);
            default -> throw new IllegalArgumentException("Unknown recurrence: " + recurrence);
        };
    }
}
