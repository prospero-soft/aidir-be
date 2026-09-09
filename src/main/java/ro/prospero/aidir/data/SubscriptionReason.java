package ro.prospero.aidir.data;

/**
 * Why a subscription period was opened. Mirrors the subscription_reason lookup table.
 *
 * <p>Note {@link #PAYMENT_FAILED}: a grace period is an ordinary period row on the same plan,
 * which is why there is no status to mutate and no grace deadline column - the row's own
 * period_end is the deadline.
 */
public enum SubscriptionReason {
    CREATED("created"),
    TRIAL("trial"),
    RENEWED("renewed"),
    UPGRADED("upgraded"),
    DOWNGRADED("downgraded"),
    PAYMENT_FAILED("payment_failed"),
    REACTIVATED("reactivated"),
    DEMOTED("demoted");

    private final String wire;
    SubscriptionReason(String wire) { this.wire = wire; }

    public String wire() { return wire; }

    public static SubscriptionReason fromWire(String v) {
        for (SubscriptionReason r : values()) if (r.wire.equals(v)) return r;
        throw new IllegalArgumentException("Unknown subscription reason: " + v);
    }
}
