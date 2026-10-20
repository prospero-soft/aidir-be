package ro.prospero.aidir.data;

/** A brief waiting for triage, with the vendor behind it. Mirrors ArticleBriefReview in aidir-fe. */
public record ArticleBriefReviewDTO(ArticleBriefDTO brief,
                                    long vendorId,
                                    String vendorName,
                                    String vendorEmail) {
}
