package ro.prospero.aidir.data;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * An editor's draft of a brief, saved while it is written. Mirrors buildBriefDraftPayload in aidir-fe
 * (src/components/editorial-desk/draft.ts). The body is only held to the article rules when the draft is
 * sent to the client, so a half-written one saves.
 *
 * <p>{@code keptImages} works as on {@link ArticleRevisionPayload}, against the images of the round being
 * written or, when a new round starts, of the round before it. A {@code featured-image} part sets the cover,
 * which only a brief that left the cover to us takes; without one the draft keeps the cover it had.
 */
public record BriefDraftPayload(@NotBlank @Size(max = 300) String headline,
                                @NotNull @Size(max = 2_000) List<@NotNull @Valid ArticleBlock> body,
                                @NotNull @Size(max = 10)
                                List<@Size(max = 500) @Pattern(regexp = DealPayload.HTTP_URL) String> sources,
                                @NotNull @Size(max = 10) List<Integer> keptImages) {
}
