package ro.prospero.aidir.data;

/** An article order in the admin's review queue, with the vendor behind it. Mirrors ArticleReview in aidir-fe. */
public record ArticleReviewDTO(ArticleOrderDTO order,
                               long vendorId,
                               String vendorName,
                               String vendorEmail) {
}
