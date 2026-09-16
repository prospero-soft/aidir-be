package ro.prospero.aidir.data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * A listing from the catalogue, as the details page reads it and as the search index is built from.
 *
 * <p>There is no approval flag here, unlike {@link ToolSubmissionDTO}: a row exists in {@code tool}
 * because it was approved, so the question the flag would answer is already settled by the row being
 * there at all. {@code approvedAt} is when that happened.
 *
 * @param categories plural, because the column is: a tool is listed under every category it was
 *                   submitted for.
 */
public record ToolDTO(Long id,
                      String name,
                      String url,
                      String pricing,
                      String shortDescription,
                      String longDescription,
                      List<String> categories,
                      List<String> tags,
                      OffsetDateTime submittedAt,
                      OffsetDateTime approvedAt) {
}
