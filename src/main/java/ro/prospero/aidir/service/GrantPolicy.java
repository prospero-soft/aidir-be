package ro.prospero.aidir.service;

/**
 * What happens to a feature allowance when a subscription period opens.
 *
 * <p>Two cases, and the split is the whole of the "grace refreshes, payment does not" rule:
 * every period beginning mints a fresh allowance, except the one that follows a customer paying
 * their way out of grace, which carries the grace period's remaining allowance instead.
 */
public sealed interface GrantPolicy {

    /** Fresh allowance, expiring when the new period does. Every beginning but one. */
    record Mint() implements GrantPolicy { }

    /** Take the named period's remaining allowance instead of minting. Grace recovery only. */
    record CarryFrom(long subscriptionId) implements GrantPolicy { }

    GrantPolicy MINT = new Mint();

    static GrantPolicy carryFrom(long subscriptionId) {
        return new CarryFrom(subscriptionId);
    }
}
