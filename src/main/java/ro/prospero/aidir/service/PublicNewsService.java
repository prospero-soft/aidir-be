package ro.prospero.aidir.service;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.SortField;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import ro.prospero.aidir.data.NewsArticleDTO;
import ro.prospero.aidir.data.NewsBrowseQuery;
import ro.prospero.aidir.data.NewsCardDTO;
import ro.prospero.aidir.data.NewsPage;
import ro.prospero.aidir.data.NewsSort;
import ro.prospero.aidir.jooq.generated.tables.records.PublishedArticleRecord;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static ro.prospero.aidir.jooq.generated.Tables.PUBLISHED_ARTICLE;
import static ro.prospero.aidir.jooq.generated.Tables.PUBLISHED_ARTICLE_IMAGE;
import static ro.prospero.aidir.jooq.generated.Tables.PUBLISHED_ARTICLE_TOPIC;

/**
 * The news pages: the list of published articles and each article whole. An article is public from its
 * {@code published_at}, so one published for a day still to come answers as if it did not exist.
 */
@Service
public class PublicNewsService {
    static final int RELATED = 3;

    /** What a card needs, which leaves out the body: lists never read it. */
    private static final List<Field<?>> CARD_FIELDS = List.of(PUBLISHED_ARTICLE.ID,
                                                              PUBLISHED_ARTICLE.SLUG,
                                                              PUBLISHED_ARTICLE.HEADLINE,
                                                              PUBLISHED_ARTICLE.EXCERPT,
                                                              PUBLISHED_ARTICLE.BYLINE,
                                                              PUBLISHED_ARTICLE.PUBLISHED_AT,
                                                              PUBLISHED_ARTICLE.READ_MINUTES,
                                                              PUBLISHED_ARTICLE.FEATURED_IMAGE_PATH,
                                                              PUBLISHED_ARTICLE.UNLABELLED);

    private final DSLContext dslContext;
    private final MediaUrls mediaUrls;

    public PublicNewsService(DSLContext dslContext, MediaUrls mediaUrls) {
        this.dslContext = dslContext;
        this.mediaUrls = mediaUrls;
    }

    public NewsPage browse(NewsBrowseQuery query) {
        Condition condition = live(OffsetDateTime.now()).and(matching(query));
        long total = dslContext.fetchCount(PUBLISHED_ARTICLE, condition);
        List<PublishedArticleRecord> rows = dslContext.select(CARD_FIELDS)
                                                      .from(PUBLISHED_ARTICLE)
                                                      .where(condition)
                                                      .orderBy(order(query.sort()))
                                                      .limit(query.size())
                                                      .offset(query.offset())
                                                      .fetchInto(PUBLISHED_ARTICLE);
        return new NewsPage(cards(rows), total, query.page(), query.size());
    }

    public Optional<NewsArticleDTO> find(String slug) {
        OffsetDateTime now = OffsetDateTime.now();
        PublishedArticleRecord row = dslContext.selectFrom(PUBLISHED_ARTICLE)
                                               .where(PUBLISHED_ARTICLE.SLUG.eq(slug))
                                               .and(live(now))
                                               .fetchOne();
        if (row == null) {
            return Optional.empty();
        }

        NewsCardDTO card = cards(List.of(row)).getFirst();
        List<String> imageUrls = dslContext.select(PUBLISHED_ARTICLE_IMAGE.PATH)
                                           .from(PUBLISHED_ARTICLE_IMAGE)
                                           .where(PUBLISHED_ARTICLE_IMAGE.PUBLISHED_ARTICLE_ID.eq(row.getId()))
                                           .orderBy(PUBLISHED_ARTICLE_IMAGE.POSITION)
                                           .fetch(PUBLISHED_ARTICLE_IMAGE.PATH)
                                           .stream()
                                           .map(mediaUrls::publicUrl)
                                           .toList();
        return Optional.of(new NewsArticleDTO(card.slug(),
                                              card.headline(),
                                              card.excerpt(),
                                              card.topics(),
                                              card.byline(),
                                              card.publishedAt(),
                                              card.readMinutes(),
                                              card.imageUrl(),
                                              card.sponsored(),
                                              ArticleJson.body(row.getBody()),
                                              imageUrls,
                                              ArticleJson.strings(row.getSources()),
                                              PublicationRules.followLinks(row.getFollowLinksUntil(), now),
                                              related(row.getId(), card.topics(), now)));
    }

    /** Other live articles: those sharing a topic with this one first, the newest first within each. */
    private List<NewsCardDTO> related(long articleId, List<String> topics, OffsetDateTime now) {
        Condition sharesTopic = topics.isEmpty() ? DSL.falseCondition()
                : PUBLISHED_ARTICLE.ID.in(DSL.select(PUBLISHED_ARTICLE_TOPIC.PUBLISHED_ARTICLE_ID)
                                             .from(PUBLISHED_ARTICLE_TOPIC)
                                             .where(PUBLISHED_ARTICLE_TOPIC.TOPIC.in(topics)));
        List<PublishedArticleRecord> rows = dslContext.select(CARD_FIELDS)
                                                      .from(PUBLISHED_ARTICLE)
                                                      .where(live(now))
                                                      .and(PUBLISHED_ARTICLE.ID.ne(articleId))
                                                      .orderBy(DSL.when(sharesTopic, 0).otherwise(1),
                                                               PUBLISHED_ARTICLE.PUBLISHED_AT.desc(),
                                                               PUBLISHED_ARTICLE.ID.desc())
                                                      .limit(RELATED)
                                                      .fetchInto(PUBLISHED_ARTICLE);
        return cards(rows);
    }

    private static Condition live(OffsetDateTime now) {
        return PUBLISHED_ARTICLE.PUBLISHED_AT.le(now);
    }

    private static Condition matching(NewsBrowseQuery query) {
        Condition condition = DSL.noCondition();
        if (query.topic() != null) {
            condition = condition.and(PUBLISHED_ARTICLE.ID.in(
                    DSL.select(PUBLISHED_ARTICLE_TOPIC.PUBLISHED_ARTICLE_ID)
                       .from(PUBLISHED_ARTICLE_TOPIC)
                       .where(PUBLISHED_ARTICLE_TOPIC.TOPIC.eq(query.topic()))));
        }
        if (query.query() != null) {
            String text = query.query();
            condition = condition.and(PUBLISHED_ARTICLE.HEADLINE.containsIgnoreCase(text)
                                      .or(PUBLISHED_ARTICLE.EXCERPT.containsIgnoreCase(text))
                                      .or(PUBLISHED_ARTICLE.BYLINE.containsIgnoreCase(text)));
        }
        if (!query.exclude().isEmpty()) {
            condition = condition.and(PUBLISHED_ARTICLE.SLUG.notIn(query.exclude()));
        }
        return condition;
    }

    /** The id breaks ties between articles published at the same moment, so paging never repeats one. */
    private static List<SortField<?>> order(NewsSort sort) {
        return sort == NewsSort.OLDEST
                ? List.of(PUBLISHED_ARTICLE.PUBLISHED_AT.asc(), PUBLISHED_ARTICLE.ID.asc())
                : List.of(PUBLISHED_ARTICLE.PUBLISHED_AT.desc(), PUBLISHED_ARTICLE.ID.desc());
    }

    private List<NewsCardDTO> cards(List<PublishedArticleRecord> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, List<String>> topics =
                dslContext.select(PUBLISHED_ARTICLE_TOPIC.PUBLISHED_ARTICLE_ID, PUBLISHED_ARTICLE_TOPIC.TOPIC)
                          .from(PUBLISHED_ARTICLE_TOPIC)
                          .where(PUBLISHED_ARTICLE_TOPIC.PUBLISHED_ARTICLE_ID.in(
                                  rows.stream().map(PublishedArticleRecord::getId).toList()))
                          .orderBy(PUBLISHED_ARTICLE_TOPIC.TOPIC)
                          .fetchGroups(PUBLISHED_ARTICLE_TOPIC.PUBLISHED_ARTICLE_ID, PUBLISHED_ARTICLE_TOPIC.TOPIC);
        return rows.stream()
                   .map(row -> new NewsCardDTO(row.getSlug(),
                                               row.getHeadline(),
                                               row.getExcerpt(),
                                               topics.getOrDefault(row.getId(), List.of()),
                                               row.getByline(),
                                               row.getPublishedAt(),
                                               row.getReadMinutes(),
                                               row.getFeaturedImagePath() == null ? null
                                                       : mediaUrls.publicUrl(row.getFeaturedImagePath()),
                                               !row.getUnlabelled()))
                   .toList();
    }
}
