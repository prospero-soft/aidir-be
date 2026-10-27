package ro.prospero.aidir.service;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Component;
import ro.prospero.aidir.data.ArticleBlock;
import ro.prospero.aidir.jooq.generated.tables.records.ArticleBriefDraftRecord;
import ro.prospero.aidir.jooq.generated.tables.records.ArticleBriefRecord;
import ro.prospero.aidir.jooq.generated.tables.records.SubmittedArticleRecord;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static ro.prospero.aidir.jooq.generated.Tables.ARTICLE_BRIEF;
import static ro.prospero.aidir.jooq.generated.Tables.ARTICLE_BRIEF_DRAFT;
import static ro.prospero.aidir.jooq.generated.Tables.ARTICLE_BRIEF_DRAFT_IMAGE;
import static ro.prospero.aidir.jooq.generated.Tables.ARTICLE_BRIEF_TOPIC;
import static ro.prospero.aidir.jooq.generated.Tables.PUBLISHED_ARTICLE;
import static ro.prospero.aidir.jooq.generated.Tables.PUBLISHED_ARTICLE_IMAGE;
import static ro.prospero.aidir.jooq.generated.Tables.PUBLISHED_ARTICLE_TOPIC;
import static ro.prospero.aidir.jooq.generated.Tables.SUBMITTED_ARTICLE;
import static ro.prospero.aidir.jooq.generated.Tables.SUBMITTED_ARTICLE_IMAGE;
import static ro.prospero.aidir.jooq.generated.Tables.SUBMITTED_ARTICLE_TOPIC;

/**
 * Copies an approved submitted article, or a completed brief's last draft, onto the news pages. Both run in
 * the transaction that records the verdict, which holds the lock on the article or brief, so the same one
 * cannot be published twice at once; publishing one that already is does nothing.
 */
@Component
public class ArticlePublisher {
    private final VendorContacts vendorContacts;

    public ArticlePublisher(VendorContacts vendorContacts) {
        this.vendorContacts = vendorContacts;
    }

    public void publishSubmitted(DSLContext tx, long articleId) {
        if (tx.fetchExists(PUBLISHED_ARTICLE, PUBLISHED_ARTICLE.SUBMITTED_ARTICLE_ID.eq(articleId))) {
            return;
        }
        SubmittedArticleRecord article = tx.fetchSingle(SUBMITTED_ARTICLE, SUBMITTED_ARTICLE.ID.eq(articleId));
        OffsetDateTime publishedAt = OffsetDateTime.now();

        insert(tx,
               new Source(articleId, null, article.getAccountId(), article.getHeadline(), article.getBody(),
                          article.getSources(), article.getFeaturedImagePath(), article.getPermanentLinks(),
                          article.getUnlabelled(), publishedAt),
               tx.select(SUBMITTED_ARTICLE_TOPIC.TOPIC)
                 .from(SUBMITTED_ARTICLE_TOPIC)
                 .where(SUBMITTED_ARTICLE_TOPIC.SUBMITTED_ARTICLE_ID.eq(articleId))
                 .fetch(SUBMITTED_ARTICLE_TOPIC.TOPIC),
               tx.select(SUBMITTED_ARTICLE_IMAGE.PATH)
                 .from(SUBMITTED_ARTICLE_IMAGE)
                 .where(SUBMITTED_ARTICLE_IMAGE.SUBMITTED_ARTICLE_ID.eq(articleId))
                 .orderBy(SUBMITTED_ARTICLE_IMAGE.POSITION)
                 .fetch(SUBMITTED_ARTICLE_IMAGE.PATH));
    }

    /** The article is the last round sent to the client, which is the one they took. */
    public void publishBrief(DSLContext tx, long briefId) {
        if (tx.fetchExists(PUBLISHED_ARTICLE, PUBLISHED_ARTICLE.ARTICLE_BRIEF_ID.eq(briefId))) {
            return;
        }
        ArticleBriefRecord brief = tx.fetchSingle(ARTICLE_BRIEF, ARTICLE_BRIEF.ID.eq(briefId));
        ArticleBriefDraftRecord draft = tx.selectFrom(ARTICLE_BRIEF_DRAFT)
                                          .where(ARTICLE_BRIEF_DRAFT.ARTICLE_BRIEF_ID.eq(briefId))
                                          .and(ARTICLE_BRIEF_DRAFT.SENT_AT.isNotNull())
                                          .orderBy(ARTICLE_BRIEF_DRAFT.ROUND.desc())
                                          .limit(1)
                                          .fetchOptional()
                                          .orElseThrow(() -> new IllegalStateException(
                                                  "Article brief " + briefId + " has no draft sent to publish"));
        String cover = draft.getFeaturedImagePath() != null ? draft.getFeaturedImagePath()
                : brief.getFeaturedImagePath();
        OffsetDateTime publishedAt =
                PublicationRules.briefPublishedAt(OffsetDateTime.now(), brief.getPreferredDate(),
                                                  ZoneId.systemDefault());

        insert(tx,
               new Source(null, briefId, brief.getAccountId(), draft.getHeadline(), draft.getBody(),
                          draft.getSources(), cover, brief.getPermanentLinks(), brief.getUnlabelled(), publishedAt),
               tx.select(ARTICLE_BRIEF_TOPIC.TOPIC)
                 .from(ARTICLE_BRIEF_TOPIC)
                 .where(ARTICLE_BRIEF_TOPIC.ARTICLE_BRIEF_ID.eq(briefId))
                 .fetch(ARTICLE_BRIEF_TOPIC.TOPIC),
               tx.select(ARTICLE_BRIEF_DRAFT_IMAGE.PATH)
                 .from(ARTICLE_BRIEF_DRAFT_IMAGE)
                 .where(ARTICLE_BRIEF_DRAFT_IMAGE.ARTICLE_BRIEF_DRAFT_ID.eq(draft.getId()))
                 .orderBy(ARTICLE_BRIEF_DRAFT_IMAGE.POSITION)
                 .fetch(ARTICLE_BRIEF_DRAFT_IMAGE.PATH));
    }

    /** What both kinds of article publish from; exactly one of the two ids is set. */
    private record Source(Long submittedArticleId,
                          Long articleBriefId,
                          long accountId,
                          String headline,
                          JSONB body,
                          JSONB sources,
                          String featuredImagePath,
                          boolean permanentLinks,
                          boolean unlabelled,
                          OffsetDateTime publishedAt) {
    }

    private void insert(DSLContext tx, Source source, List<String> topics, List<String> imagePaths) {
        List<ArticleBlock> body = ArticleJson.body(source.body());
        String byline = VendorContacts.orUnknown(vendorContacts.of(tx, List.of(source.accountId())),
                                                 source.accountId())
                                      .name();
        if (byline == null) {
            throw new IllegalStateException("No vendor account " + source.accountId() + " to credit");
        }

        long id = tx.insertInto(PUBLISHED_ARTICLE)
                    .set(PUBLISHED_ARTICLE.SLUG, freeSlug(tx, PublicationRules.slug(source.headline())))
                    .set(PUBLISHED_ARTICLE.SUBMITTED_ARTICLE_ID, source.submittedArticleId())
                    .set(PUBLISHED_ARTICLE.ARTICLE_BRIEF_ID, source.articleBriefId())
                    .set(PUBLISHED_ARTICLE.ACCOUNT_ID, source.accountId())
                    .set(PUBLISHED_ARTICLE.HEADLINE, source.headline())
                    .set(PUBLISHED_ARTICLE.EXCERPT, PublicationRules.excerpt(body))
                    .set(PUBLISHED_ARTICLE.BODY, source.body())
                    .set(PUBLISHED_ARTICLE.SOURCES, source.sources())
                    .set(PUBLISHED_ARTICLE.FEATURED_IMAGE_PATH, source.featuredImagePath())
                    .set(PUBLISHED_ARTICLE.READ_MINUTES, PublicationRules.readMinutes(body))
                    .set(PUBLISHED_ARTICLE.BYLINE, byline)
                    .set(PUBLISHED_ARTICLE.UNLABELLED, source.unlabelled())
                    .set(PUBLISHED_ARTICLE.FOLLOW_LINKS_UNTIL,
                         PublicationRules.followLinksUntil(source.publishedAt(), source.permanentLinks()))
                    .set(PUBLISHED_ARTICLE.PUBLISHED_AT, source.publishedAt())
                    .returningResult(PUBLISHED_ARTICLE.ID)
                    .fetchSingle(PUBLISHED_ARTICLE.ID);

        for (String topic : topics) {
            tx.insertInto(PUBLISHED_ARTICLE_TOPIC)
              .set(PUBLISHED_ARTICLE_TOPIC.PUBLISHED_ARTICLE_ID, id)
              .set(PUBLISHED_ARTICLE_TOPIC.TOPIC, topic)
              .execute();
        }
        for (int position = 0; position < imagePaths.size(); position++) {
            tx.insertInto(PUBLISHED_ARTICLE_IMAGE)
              .set(PUBLISHED_ARTICLE_IMAGE.PUBLISHED_ARTICLE_ID, id)
              .set(PUBLISHED_ARTICLE_IMAGE.POSITION, position)
              .set(PUBLISHED_ARTICLE_IMAGE.PATH, imagePaths.get(position))
              .execute();
        }
    }

    /**
     * The headline's slug, numbered past any article that already has it. Two articles with the same headline
     * published at the same moment can still both pick the same number; the unique constraint then refuses
     * the second, and its approval is rolled back to be tried again.
     */
    private static String freeSlug(DSLContext tx, String base) {
        Condition sameBase = PUBLISHED_ARTICLE.SLUG.eq(base).or(PUBLISHED_ARTICLE.SLUG.startsWith(base + "-"));
        Set<String> taken = new HashSet<>(tx.select(PUBLISHED_ARTICLE.SLUG)
                                            .from(PUBLISHED_ARTICLE)
                                            .where(sameBase)
                                            .fetch(PUBLISHED_ARTICLE.SLUG));
        return PublicationRules.uniqueSlug(base, taken);
    }
}
