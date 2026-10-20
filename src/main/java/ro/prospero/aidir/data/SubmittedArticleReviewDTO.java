package ro.prospero.aidir.data;

/**
 * A submitted article in the compliance review queue, with the vendor behind it. Mirrors SubmittedArticleReview
 * in aidir-fe.
 */
public record SubmittedArticleReviewDTO(SubmittedArticleDTO article,
                                        long vendorId,
                                        String vendorName,
                                        String vendorEmail) {
}
