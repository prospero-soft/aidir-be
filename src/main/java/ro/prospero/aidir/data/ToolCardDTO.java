package ro.prospero.aidir.data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * One row of the directory. Deliberately fatter than {@link ToolSearchHit}: the browse page renders a card
 * per result, and hydrating each one from {@code /api/tool/details/{id}} would be a request per row.
 *
 * <p>{@code logoUrl} is already prefixed with the uploads public base path, so it addresses the file as-is
 * and the front end never has to know where media is mounted. It is null when the vendor uploaded no logo.
 */
public record ToolCardDTO(Long id,
                          String name,
                          String shortDescription,
                          String url,
                          String pricing,
                          List<String> categories,
                          List<String> tags,
                          String logoUrl,
                          OffsetDateTime approvedAt) {
}
