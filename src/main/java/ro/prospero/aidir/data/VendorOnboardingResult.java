package ro.prospero.aidir.data;

/**
 * What one vendor onboarding produced: the account that can now sign in, and the submission now parked
 * in the moderation queue.
 */
public record VendorOnboardingResult(long accountId, long submissionId) {
}
