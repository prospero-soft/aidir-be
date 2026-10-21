package ro.prospero.aidir.data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * One round of an editor's draft of a brief, and what the client said of it. Mirrors ArticleBriefDraft in
 * aidir-fe (src/types/articleBrief.ts). A null {@code sentAt} is the round still being written, which only
 * the writing desk is shown.
 *
 * <p>{@code featuredImageUrl} is the cover in effect: the one the editor chose when the brief left it to us,
 * otherwise the brief's own; null while neither exists. {@code imageUrls} are the in-article images, numbered
 * as the body's image blocks number them.
 */
public record ArticleBriefDraftDTO(int round,
                                   String headline,
                                   List<ArticleBlock> body,
                                   List<String> sources,
                                   String featuredImageUrl,
                                   List<String> imageUrls,
                                   OffsetDateTime updatedAt,
                                   OffsetDateTime sentAt,
                                   String feedback,
                                   OffsetDateTime feedbackAt) {
}
