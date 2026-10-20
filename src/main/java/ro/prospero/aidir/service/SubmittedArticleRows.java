package ro.prospero.aidir.service;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.SortField;
import org.springframework.stereotype.Component;
import ro.prospero.aidir.data.SubmittedArticleDTO;
import ro.prospero.aidir.data.SubmittedArticleStatus;
import ro.prospero.aidir.jooq.generated.tables.records.SubmittedArticleRecord;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static ro.prospero.aidir.jooq.generated.Tables.SUBMITTED_ARTICLE;
import static ro.prospero.aidir.jooq.generated.Tables.SUBMITTED_ARTICLE_IMAGE;
import static ro.prospero.aidir.jooq.generated.Tables.SUBMITTED_ARTICLE_TOPIC;

/**
 * The submitted article tables, read into {@link SubmittedArticleDTO}. The vendor and review services both
 * go through here, so an article reads the same wherever it is shown.
 *
 * <p>Every method that queries takes the DSLContext to run on, so callers can use it inside a transaction.
 */
@Component
public class SubmittedArticleRows {
    private final MediaUrls mediaUrls;

    public SubmittedArticleRows(MediaUrls mediaUrls) {
        this.mediaUrls = mediaUrls;
    }

    /** An article with the account it belongs to, which the DTO leaves out. */
    public record Stored(long accountId, SubmittedArticleDTO article) {
    }

    public List<Stored> read(DSLContext ctx, Condition condition, SortField<?>... order) {
        List<SubmittedArticleRecord> rows = ctx.selectFrom(SUBMITTED_ARTICLE).where(condition).orderBy(order).fetch();
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(SubmittedArticleRecord::getId).toList();

        // One query each for the topics and images of every article read, not one per article.
        Map<Long, List<String>> topics =
                ctx.select(SUBMITTED_ARTICLE_TOPIC.SUBMITTED_ARTICLE_ID, SUBMITTED_ARTICLE_TOPIC.TOPIC)
                   .from(SUBMITTED_ARTICLE_TOPIC)
                   .where(SUBMITTED_ARTICLE_TOPIC.SUBMITTED_ARTICLE_ID.in(ids))
                   .orderBy(SUBMITTED_ARTICLE_TOPIC.TOPIC)
                   .fetchGroups(SUBMITTED_ARTICLE_TOPIC.SUBMITTED_ARTICLE_ID, SUBMITTED_ARTICLE_TOPIC.TOPIC);
        Map<Long, List<String>> images =
                ctx.select(SUBMITTED_ARTICLE_IMAGE.SUBMITTED_ARTICLE_ID, SUBMITTED_ARTICLE_IMAGE.PATH)
                   .from(SUBMITTED_ARTICLE_IMAGE)
                   .where(SUBMITTED_ARTICLE_IMAGE.SUBMITTED_ARTICLE_ID.in(ids))
                   .orderBy(SUBMITTED_ARTICLE_IMAGE.POSITION)
                   .fetchGroups(SUBMITTED_ARTICLE_IMAGE.SUBMITTED_ARTICLE_ID, SUBMITTED_ARTICLE_IMAGE.PATH);

        return rows.stream()
                   .map(row -> new Stored(row.getAccountId(),
                                          toDto(row,
                                                topics.getOrDefault(row.getId(), List.of()),
                                                images.getOrDefault(row.getId(), List.of()))))
                   .toList();
    }

    public Optional<Stored> readOne(DSLContext ctx, Condition condition) {
        return read(ctx, condition).stream().findFirst();
    }

    private SubmittedArticleDTO toDto(SubmittedArticleRecord row, List<String> topics, List<String> imagePaths) {
        return new SubmittedArticleDTO(row.getId(),
                                       "AIDA-%d-%04d".formatted(row.getCreatedAt().getYear(), row.getId()),
                                       SubmittedArticleStatus.fromWire(row.getStatus()),
                                       row.getHeadline(),
                                       topics,
                                       mediaUrls.publicUrl(row.getFeaturedImagePath()),
                                       ArticleJson.body(row.getBody()),
                                       imagePaths.stream().map(mediaUrls::publicUrl).toList(),
                                       ArticleJson.strings(row.getSources()),
                                       new SubmittedArticleDTO.Options(row.getLinks(), row.getPermanentLinks(),
                                                                       row.getUnlabelled()),
                                       ArticleJson.quote(row.getQuote()),
                                       row.getReviewNotes(),
                                       row.getCreatedAt(),
                                       row.getReviewedAt(),
                                       row.getPaidAt());
    }
}
