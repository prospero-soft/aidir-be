package ro.prospero.aidir.data;

/**
 * A brief as our side sees it: with the vendor behind it, and once assigned, the editor writing it. Mirrors
 * ArticleBriefReview in aidir-fe. Triage, which comes before any editor, leaves {@code editor} null.
 * {@code assignedToYou} says whether the caller is that editor, so the writing page knows whether it may write.
 */
public record ArticleBriefReviewDTO(ArticleBriefDTO brief,
                                    long vendorId,
                                    String vendorName,
                                    String vendorEmail,
                                    Editor editor,
                                    boolean assignedToYou) {

    public record Editor(long id, String name) {
    }
}
