package ro.prospero.aidir.data;

/**
 * How a subscription period ended, and set only when one is cut short. Mirrors the
 * subscription_end lookup table.
 *
 * <p>There is deliberately no {@code EXPIRED}: a row whose period_end has passed with ended_as
 * still null is expired by definition of its own range, so nothing has to stamp it and there is
 * no window in which the marker and the dates disagree.
 */
public enum SubscriptionEnd {
    SUPERSEDED("superseded"),
    CANCELLED("cancelled");

    private final String wire;
    SubscriptionEnd(String wire) { this.wire = wire; }

    public String wire() { return wire; }

    public static SubscriptionEnd fromWire(String v) {
        for (SubscriptionEnd e : values()) if (e.wire.equals(v)) return e;
        throw new IllegalArgumentException("Unknown subscription end: " + v);
    }
}
