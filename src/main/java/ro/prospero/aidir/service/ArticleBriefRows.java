package ro.prospero.aidir.service;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.SortField;
import org.springframework.stereotype.Component;
import ro.prospero.aidir.data.ArticleBriefDTO;
import ro.prospero.aidir.data.ArticleBriefStatus;
import ro.prospero.aidir.data.WritingTier;
import ro.prospero.aidir.jooq.generated.tables.records.ArticleBriefRecord;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static ro.prospero.aidir.jooq.generated.Tables.ARTICLE_BRIEF;
import static ro.prospero.aidir.jooq.generated.Tables.ARTICLE_BRIEF_TOPIC;

/**
 * The article brief tables, read into {@link ArticleBriefDTO}. The vendor and triage services both go
 * through here, so a brief reads the same wherever it is shown.
 *
 * <p>Every method that queries takes the DSLContext to run on, so callers can use it inside a transaction.
 */
@Component
public class ArticleBriefRows {
    private final MediaUrls mediaUrls;

    public ArticleBriefRows(MediaUrls mediaUrls) {
        this.mediaUrls = mediaUrls;
    }

    /** A brief with the account it belongs to, which the DTO leaves out. */
    public record Stored(long accountId, ArticleBriefDTO brief) {
    }

    public List<Stored> read(DSLContext ctx, Condition condition, SortField<?>... order) {
        List<ArticleBriefRecord> rows = ctx.selectFrom(ARTICLE_BRIEF).where(condition).orderBy(order).fetch();
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(ArticleBriefRecord::getId).toList();

        // One query for the topics of every brief read, not one per brief.
        Map<Long, List<String>> topics =
                ctx.select(ARTICLE_BRIEF_TOPIC.ARTICLE_BRIEF_ID, ARTICLE_BRIEF_TOPIC.TOPIC)
                   .from(ARTICLE_BRIEF_TOPIC)
                   .where(ARTICLE_BRIEF_TOPIC.ARTICLE_BRIEF_ID.in(ids))
                   .orderBy(ARTICLE_BRIEF_TOPIC.TOPIC)
                   .fetchGroups(ARTICLE_BRIEF_TOPIC.ARTICLE_BRIEF_ID, ARTICLE_BRIEF_TOPIC.TOPIC);

        return rows.stream()
                   .map(row -> new Stored(row.getAccountId(), toDto(row, topics.getOrDefault(row.getId(), List.of()))))
                   .toList();
    }

    public Optional<Stored> readOne(DSLContext ctx, Condition condition) {
        return read(ctx, condition).stream().findFirst();
    }

    private ArticleBriefDTO toDto(ArticleBriefRecord row, List<String> topics) {
        return new ArticleBriefDTO(row.getId(),
                                   "AIDB-%d-%04d".formatted(row.getCreatedAt().getYear(), row.getId()),
                                   ArticleBriefStatus.fromWire(row.getStatus()),
                                   row.getHeadline(),
                                   row.getBrief(),
                                   row.getAudience(),
                                   ArticleJson.strings(row.getKeyMessages()),
                                   row.getPreferredDate(),
                                   row.getFeaturedImagePath() == null ? null
                                           : mediaUrls.publicUrl(row.getFeaturedImagePath()),
                                   topics,
                                   ArticleJson.strings(row.getSources()),
                                   new ArticleBriefDTO.Options(WritingTier.fromWire(row.getWritingTier()),
                                                               row.getLinks(), row.getPermanentLinks(),
                                                               row.getUnlabelled()),
                                   ArticleJson.quote(row.getQuote()),
                                   row.getDeclineReason(),
                                   row.getCreatedAt(),
                                   row.getTriagedAt(),
                                   row.getPaidAt());
    }
}
