package ro.prospero.aidir.data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Why an article order was turned down. The vendor is told, so it is required. */
public record RejectArticleRequest(@NotBlank @Size(max = 1000) String reason) {
}
