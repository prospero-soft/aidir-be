package ro.prospero.aidir.data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * A published article, whole: the card's fields plus what the article page renders. The body's image blocks
 * number {@code imageUrls}. {@code followLinks} is false once the follow links the order paid for have run
 * out, after which the body's links are marked sponsored and nofollow. {@code related} are other articles,
 * those sharing a topic first. Mirrors NewsArticle in aidir-fe (src/types/news.ts).
 */
public record NewsArticleDTO(String slug,
                             String headline,
                             String excerpt,
                             List<String> topics,
                             String byline,
                             OffsetDateTime publishedAt,
                             int readMinutes,
                             String imageUrl,
                             boolean sponsored,
                             List<ArticleBlock> body,
                             List<String> imageUrls,
                             List<String> sources,
                             boolean followLinks,
                             List<NewsCardDTO> related) {
}
