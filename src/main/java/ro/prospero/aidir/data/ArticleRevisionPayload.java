package ro.prospero.aidir.data;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A vendor's revision of an article that review sent back to them. Mirrors buildRevisionPayload in aidir-fe
 * (src/components/article-submission/articlePayload.ts). It replaces what {@link ArticleSubmissionPayload}
 * sent, except the options and the price: the order was paid as first placed, so neither changes.
 *
 * <p>{@code keptImages} has an entry for each image the body's image blocks number. An entry is the position
 * of the stored image that image keeps, or null for a new one, which travels as an
 * {@code additional-image-<n>} part numbered by its place in the body. With no {@code featured-image} part
 * the stored cover is kept.
 */
public record ArticleRevisionPayload(@NotBlank @Size(max = 120) String headline,
                                     @NotNull @Size(min = 1, max = 3) List<@NotBlank String> topics,
                                     @NotNull @Size(min = 1, max = 2_000) List<@NotNull @Valid ArticleBlock> body,
                                     @NotNull @Size(max = 10)
                                     List<@Size(max = 500) @Pattern(regexp = DealPayload.HTTP_URL) String> sources,
                                     @NotNull @Valid ArticleConsents consents,
                                     @NotNull @Size(max = 10) List<Integer> keptImages) {
}
