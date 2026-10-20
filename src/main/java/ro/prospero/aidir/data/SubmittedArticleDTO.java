package ro.prospero.aidir.data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * An article a vendor wrote and sent us to publish. The body's image blocks number {@code imageUrls}; image
 * fields are public URLs. Mirrors SubmittedArticle in aidir-fe (src/types/submittedArticle.ts).
 *
 * <p>{@code reference} is what the vendor is told to quote: {@code AIDA-<year submitted>-<id>}.
 * {@code reviewNotes} are the compliance issues it was last sent back with. A null {@code paidAt} means
 * unpaid, which every article is until there is a payment provider.
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
                                  OffsetDateTime createdAt,
                                  OffsetDateTime reviewedAt,
                                  OffsetDateTime paidAt) {

    /** {@code links} is the number of follow links billed. */
    public record Options(int links, boolean permanentLinks, boolean unlabelled) {
    }
}
