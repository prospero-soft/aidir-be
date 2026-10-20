package ro.prospero.aidir.data;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * One block of a submitted article's body, as aidir-fe's article editor sets it out; {@code kind} says which.
 * Mirrors ArticleBodyBlock in aidir-fe (src/types/submittedArticle.ts). The editor drops empty blocks, so every
 * block here has something in it.
 *
 * <p>What the article's template asks of these - how many links, pull quotes and images - is checked by
 * {@link ro.prospero.aidir.service.ArticleBodyRules}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ArticleBlock.Paragraph.class, name = "paragraph"),
        @JsonSubTypes.Type(value = ArticleBlock.Heading.class, name = "heading"),
        @JsonSubTypes.Type(value = ArticleBlock.ListBlock.class, name = "list"),
        @JsonSubTypes.Type(value = ArticleBlock.PullQuote.class, name = "pullQuote"),
        @JsonSubTypes.Type(value = ArticleBlock.Image.class, name = "image"),
})
public sealed interface ArticleBlock {

    /** A run of text with one style throughout. {@code href} is null for a run that is not a link. */
    record Inline(@NotNull @Size(max = 20_000) String text,
                  @Size(max = 500) String href,
                  boolean bold,
                  boolean italic) {
    }

    record Paragraph(@NotNull @Size(min = 1, max = 500) List<@NotNull @Valid Inline> content) implements ArticleBlock {
    }

    record Heading(@NotBlank @Size(max = 300) String text) implements ArticleBlock {
    }

    record ListBlock(boolean ordered,
                     @NotNull @Size(min = 1, max = 100)
                     List<@NotNull @Size(min = 1, max = 500) List<@NotNull @Valid Inline>> items)
            implements ArticleBlock {
    }

    /** A line lifted from the article and set large. */
    record PullQuote(@NotBlank @Size(max = 500) String text) implements ArticleBlock {
    }

    /** {@code image} numbers the order's in-article images: the request's {@code additional-image-<n>} part. */
    record Image(@Min(0) int image, @NotNull @Size(max = 300) String alt) implements ArticleBlock {
    }
}
