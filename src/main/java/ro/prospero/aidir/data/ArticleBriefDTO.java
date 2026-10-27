package ro.prospero.aidir.data;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * A vendor's brief for an article our editors write. Mirrors ArticleBrief in aidir-fe
 * (src/types/articleBrief.ts). {@code featuredImageUrl} is a public URL, or null when the vendor left the
 * cover to us.
 *
 * <p>{@code reference} is what the vendor is told to quote: {@code AIDB-<year placed>-<id>}.
 * {@code declineReason} is set once triage turns the brief down. {@code drafts} are the rounds of writing,
 * oldest first; the vendor is only sent the rounds that were sent to them. A null {@code paidAt} means unpaid.
 * {@code publication} is set once the brief is completed and its article published.
 */
public record ArticleBriefDTO(long id,
                              String reference,
                              ArticleBriefStatus status,
                              String headline,
                              String brief,
                              String audience,
                              List<String> keyMessages,
                              LocalDate preferredDate,
                              String featuredImageUrl,
                              List<String> topics,
                              List<String> sources,
                              Options options,
                              ArticleQuoteDTO quote,
                              String declineReason,
                              List<ArticleBriefDraftDTO> drafts,
                              OffsetDateTime createdAt,
                              OffsetDateTime triagedAt,
                              OffsetDateTime completedAt,
                              OffsetDateTime paidAt,
                              ArticlePublicationDTO publication) {

    /** {@code links} is the number of follow links the vendor chose. */
    public record Options(WritingTier writing, int links, boolean permanentLinks, boolean unlabelled) {
    }
}
