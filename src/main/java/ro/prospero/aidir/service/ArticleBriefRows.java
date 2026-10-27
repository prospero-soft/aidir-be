package ro.prospero.aidir.service;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.SortField;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;
import ro.prospero.aidir.data.ArticleBriefDTO;
import ro.prospero.aidir.data.ArticlePublicationDTO;
import ro.prospero.aidir.data.ArticleBriefDraftDTO;
import ro.prospero.aidir.data.ArticleBriefStatus;
import ro.prospero.aidir.data.WritingTier;
import ro.prospero.aidir.jooq.generated.tables.records.ArticleBriefDraftRecord;
import ro.prospero.aidir.jooq.generated.tables.records.ArticleBriefRecord;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static ro.prospero.aidir.jooq.generated.Tables.ARTICLE_BRIEF;
import static ro.prospero.aidir.jooq.generated.Tables.ARTICLE_BRIEF_DRAFT;
import static ro.prospero.aidir.jooq.generated.Tables.ARTICLE_BRIEF_DRAFT_IMAGE;
import static ro.prospero.aidir.jooq.generated.Tables.ARTICLE_BRIEF_TOPIC;
import static ro.prospero.aidir.jooq.generated.Tables.PUBLISHED_ARTICLE;

/**
 * The article brief tables, read into {@link ArticleBriefDTO}. The vendor, triage and writing services all go
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

    /** Which rounds of drafting a read includes. */
    public enum DraftScope {
        /** Only the rounds sent to the client: what the vendor is shown. */
        SENT,
        /** The round being written as well: what the writing desk is shown. */
        ALL
    }

    /** A brief with the accounts it belongs to and is assigned to, which the DTO leaves out. */
    public record Stored(long accountId, Long editorId, ArticleBriefDTO brief) {
    }

    public List<Stored> read(DSLContext ctx, Condition condition, DraftScope scope, SortField<?>... order) {
        List<ArticleBriefRecord> rows = ctx.selectFrom(ARTICLE_BRIEF).where(condition).orderBy(order).fetch();
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(ArticleBriefRecord::getId).toList();

        // One query each for the topics, drafts, draft images and publications of every brief read, not one per
        // brief.
        Map<Long, List<String>> topics =
                ctx.select(ARTICLE_BRIEF_TOPIC.ARTICLE_BRIEF_ID, ARTICLE_BRIEF_TOPIC.TOPIC)
                   .from(ARTICLE_BRIEF_TOPIC)
                   .where(ARTICLE_BRIEF_TOPIC.ARTICLE_BRIEF_ID.in(ids))
                   .orderBy(ARTICLE_BRIEF_TOPIC.TOPIC)
                   .fetchGroups(ARTICLE_BRIEF_TOPIC.ARTICLE_BRIEF_ID, ARTICLE_BRIEF_TOPIC.TOPIC);
        List<ArticleBriefDraftRecord> drafts =
                ctx.selectFrom(ARTICLE_BRIEF_DRAFT)
                   .where(ARTICLE_BRIEF_DRAFT.ARTICLE_BRIEF_ID.in(ids))
                   .and(scope == DraftScope.SENT ? ARTICLE_BRIEF_DRAFT.SENT_AT.isNotNull() : DSL.noCondition())
                   .orderBy(ARTICLE_BRIEF_DRAFT.ROUND)
                   .fetch();
        Map<Long, List<String>> images = drafts.isEmpty() ? Map.of()
                : ctx.select(ARTICLE_BRIEF_DRAFT_IMAGE.ARTICLE_BRIEF_DRAFT_ID, ARTICLE_BRIEF_DRAFT_IMAGE.PATH)
                     .from(ARTICLE_BRIEF_DRAFT_IMAGE)
                     .where(ARTICLE_BRIEF_DRAFT_IMAGE.ARTICLE_BRIEF_DRAFT_ID.in(
                             drafts.stream().map(ArticleBriefDraftRecord::getId).toList()))
                     .orderBy(ARTICLE_BRIEF_DRAFT_IMAGE.POSITION)
                     .fetchGroups(ARTICLE_BRIEF_DRAFT_IMAGE.ARTICLE_BRIEF_DRAFT_ID, ARTICLE_BRIEF_DRAFT_IMAGE.PATH);
        Map<Long, ArticlePublicationDTO> publications =
                ctx.select(PUBLISHED_ARTICLE.ARTICLE_BRIEF_ID, PUBLISHED_ARTICLE.SLUG, PUBLISHED_ARTICLE.PUBLISHED_AT)
                   .from(PUBLISHED_ARTICLE)
                   .where(PUBLISHED_ARTICLE.ARTICLE_BRIEF_ID.in(ids))
                   .fetchMap(PUBLISHED_ARTICLE.ARTICLE_BRIEF_ID, r -> new ArticlePublicationDTO(r.value2(), r.value3()));
        Map<Long, List<ArticleBriefDraftRecord>> draftsByBrief =
                drafts.stream().collect(Collectors.groupingBy(ArticleBriefDraftRecord::getArticleBriefId));

        return rows.stream()
                   .map(row -> {
                       List<ArticleBriefDraftDTO> rounds =
                               draftsByBrief.getOrDefault(row.getId(), List.of()).stream()
                                            .map(draft -> toDto(row, draft,
                                                                images.getOrDefault(draft.getId(), List.of())))
                                            .toList();
                       return new Stored(row.getAccountId(), row.getEditorId(),
                                         toDto(row, topics.getOrDefault(row.getId(), List.of()), rounds,
                                               publications.get(row.getId())));
                   })
                   .toList();
    }

    public Optional<Stored> readOne(DSLContext ctx, Condition condition, DraftScope scope) {
        return read(ctx, condition, scope).stream().findFirst();
    }

    private ArticleBriefDTO toDto(ArticleBriefRecord row, List<String> topics, List<ArticleBriefDraftDTO> drafts,
                                  ArticlePublicationDTO publication) {
        return new ArticleBriefDTO(row.getId(),
                                   "AIDB-%d-%04d".formatted(row.getCreatedAt().getYear(), row.getId()),
                                   ArticleBriefStatus.fromWire(row.getStatus()),
                                   row.getHeadline(),
                                   row.getBrief(),
                                   row.getAudience(),
                                   ArticleJson.strings(row.getKeyMessages()),
                                   row.getPreferredDate(),
                                   url(row.getFeaturedImagePath()),
                                   topics,
                                   ArticleJson.strings(row.getSources()),
                                   new ArticleBriefDTO.Options(WritingTier.fromWire(row.getWritingTier()),
                                                               row.getLinks(), row.getPermanentLinks(),
                                                               row.getUnlabelled()),
                                   ArticleJson.quote(row.getQuote()),
                                   row.getDeclineReason(),
                                   drafts,
                                   row.getCreatedAt(),
                                   row.getTriagedAt(),
                                   row.getCompletedAt(),
                                   row.getPaidAt(),
                                   publication);
    }

    private ArticleBriefDraftDTO toDto(ArticleBriefRecord brief, ArticleBriefDraftRecord draft,
                                       List<String> imagePaths) {
        String cover = draft.getFeaturedImagePath() != null ? draft.getFeaturedImagePath()
                : brief.getFeaturedImagePath();
        return new ArticleBriefDraftDTO(draft.getRound(),
                                        draft.getHeadline(),
                                        ArticleJson.body(draft.getBody()),
                                        ArticleJson.strings(draft.getSources()),
                                        url(cover),
                                        imagePaths.stream().map(mediaUrls::publicUrl).toList(),
                                        draft.getUpdatedAt(),
                                        draft.getSentAt(),
                                        draft.getFeedback(),
                                        draft.getFeedbackAt());
    }

    private String url(String path) {
        return path == null ? null : mediaUrls.publicUrl(path);
    }
}
