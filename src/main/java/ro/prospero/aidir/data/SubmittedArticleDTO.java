package ro.prospero.aidir.data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * An article a vendor wrote and sent us to publish. The body's image blocks number {@code imageUrls}; image
 * fields are public URLs. Mirrors SubmittedArticle in aidir-fe (src/types/submittedArticle.ts).
 *
 * <p>{@code reference} is what the vendor is told to quote: {@code AIDA-<year submitted>-<id>}.
 * {@code reviewNotes} are the compliance issues the vendor is to fix while the article waits for their
 * revision, and null otherwise; {@code reviewRounds} are every time review sent it back, oldest first.
 * {@code submittedAt} is when it last went to review. A null {@code paidAt} means unpaid, which every article
 * is until there is a payment provider.
 */
public record SubmittedArticleDTO(long id,
                                  String reference,
                                  SubmittedArticleStatus status,
                                  String headline,
                                  List<String> topics,
                                  String featuredImageUrl,
                                  List<ArticleBlock> body,
                                  List<String> imageUrls,
                                  List<String> sources,
                                  Options options,
                                  ArticleQuoteDTO quote,
                                  String reviewNotes,
                                  List<ReviewRound> reviewRounds,
                                  OffsetDateTime createdAt,
                                  OffsetDateTime submittedAt,
                                  OffsetDateTime reviewedAt,
                                  OffsetDateTime paidAt) {

    /**
     * {@code links} and {@code images} are the follow links and in-article images billed, which a revision
     * may not exceed.
     */
    public record Options(int links, int images, boolean permanentLinks, boolean unlabelled) {
    }

    /** {@code resubmittedAt} is null while the vendor has yet to send the revision. */
    public record ReviewRound(String notes, OffsetDateTime requestedAt, OffsetDateTime resubmittedAt) {
    }
}
