package ro.prospero.aidir.data;

import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * A brief for an article our editors write. Mirrors buildArticleRequestPayload in aidir-fe
 * (src/components/editorial-article/articlePayload.ts). An optional {@code featured-image} part travels
 * beside it.
 *
 * <p>{@code audience} is free text: the form's audience options are still placeholders. {@code quotedTotal}
 * is checked as on {@link ArticleSubmissionPayload}.
 */
public record ArticleRequestPayload(@NotBlank @Size(max = 300) String headline,
                                    @NotBlank @Size(max = 700) String brief,
                                    @NotNull @Size(min = 1, max = 3) List<@NotBlank String> topics,
                                    @Size(max = 100) String audience,
                                    @NotNull @Size(min = 3, max = 5) List<@NotBlank @Size(max = 200) String> keyMessages,
                                    @NotNull @Size(max = 10)
                                    List<@Size(max = 500) @Pattern(regexp = DealPayload.HTTP_URL) String> sources,
                                    @FutureOrPresent(message = "The preferred date can't be in the past")
                                    LocalDate preferredDate,
                                    @NotNull @Valid Options options,
                                    @NotNull @Valid ArticleConsents consents,
                                    @NotNull Integer quotedTotal) {

    public record Options(@NotNull WritingTier writing,
                          @Min(1) @Max(3) int links,
                          boolean permanentLinks,
                          boolean unlabelled) {
    }
}
