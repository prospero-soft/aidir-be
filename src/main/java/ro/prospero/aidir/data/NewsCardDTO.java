package ro.prospero.aidir.data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * A published article as the news list shows it. {@code imageUrl} is a public URL, or null for an article
 * without a cover. {@code sponsored} is false only for an order that paid to drop the label. Mirrors NewsCard
 * in aidir-fe (src/types/news.ts).
 */
public record NewsCardDTO(String slug,
                          String headline,
                          String excerpt,
                          List<String> topics,
                          String byline,
                          OffsetDateTime publishedAt,
                          int readMinutes,
                          String imageUrl,
                          boolean sponsored) {
}
