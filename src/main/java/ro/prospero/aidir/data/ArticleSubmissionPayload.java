package ro.prospero.aidir.data;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * An article the vendor wrote, sent for editorial review. Mirrors buildArticlePayload in aidir-fe
 * (src/components/article-submission/articlePayload.ts). The images travel as multipart parts beside it:
 * {@code featured-image}, and {@code additional-image-<n>} for each extra one.
 *
 * <p>What Bean Validation cannot say - the word count, the links in the body, the topics and the images -
 * is checked by {@link ro.prospero.aidir.service.ArticleOrderService}.
 *
 * <p>{@code quotedTotal} is the total the vendor was shown. The backend prices the order itself and refuses
 * it when the two differ, so nobody is billed a price they did not see.
 */
public record ArticleSubmissionPayload(@NotBlank @Size(max = 120) String headline,
                                       @NotNull @Size(min = 1, max = 3) List<@NotBlank String> topics,
                                       @NotBlank @Size(max = 100_000) String body,
                                       @NotNull @Size(max = 10)
                                       List<@Size(max = 500) @Pattern(regexp = DealPayload.HTTP_URL) String> sources,
                                       @NotNull @Valid Options options,
                                       @NotNull @Valid ArticleConsents consents,
                                       @NotNull Integer quotedTotal) {

    /** The vendor wrote the article, so the writing tiers are not on offer here. */
    public record Options(boolean permanentLinks, boolean unlabelled) {
    }
}
