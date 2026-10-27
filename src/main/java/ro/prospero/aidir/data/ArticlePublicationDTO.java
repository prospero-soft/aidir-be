package ro.prospero.aidir.data;

import java.time.OffsetDateTime;

/**
 * Where and from when an article is on the news pages: {@code /news/<slug>}. {@code publishedAt} can still be
 * to come, for a brief published on the day the vendor asked for. Mirrors ArticlePublication in aidir-fe
 * (src/types/news.ts).
 */
public record ArticlePublicationDTO(String slug, OffsetDateTime publishedAt) {
}
