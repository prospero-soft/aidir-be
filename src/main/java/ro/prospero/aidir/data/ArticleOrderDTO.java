package ro.prospero.aidir.data;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * A sponsored article order of either kind. {@code submitted} is set for an article the vendor wrote and
 * {@code editorial} for a brief, never both. Image fields are public URLs. Mirrors ArticleOrder in aidir-fe
 * (src/types/articleOrder.ts).
 *
 * <p>{@code reference} is what the vendor is told to quote: {@code AIDP-<year placed>-<id>}. A null
 * {@code paidAt} means unpaid, which every order is until there is a payment provider.
 */
public record ArticleOrderDTO(long id,
                              String reference,
                              ArticleOrderKind kind,
                              ArticleOrderStatus status,
                              String headline,
                              List<String> topics,
                              String featuredImageUrl,
                              List<String> sources,
                              Submitted submitted,
                              Editorial editorial,
                              Options options,
                              ArticleQuoteDTO quote,
                              String rejectionReason,
                              OffsetDateTime createdAt,
                              OffsetDateTime reviewedAt,
                              OffsetDateTime paidAt) {

    public record Submitted(String body, List<String> additionalImageUrls) {
    }

    public record Editorial(String brief,
                            String audience,
                            List<String> keyMessages,
                            LocalDate preferredDate,
                            WritingTier writing) {
    }

    /** {@code links} is the number of follow links billed. */
    public record Options(int links, boolean permanentLinks, boolean unlabelled) {
    }
}
