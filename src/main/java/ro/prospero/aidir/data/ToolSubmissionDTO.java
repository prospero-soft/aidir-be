package ro.prospero.aidir.data;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * A row of the moderation queue, and the body the admin screen posts back to approve or reject it.
 *
 * <p>{@code approved} is three-valued, which is the whole reason this is separate from {@link ToolDTO}:
 * null is "not looked at yet", and it is the only value the queue can currently hand out, since
 * {@code ToolSubmissionService.getQueue} asks for exactly the undecided rows.
 */
@Data
public class ToolSubmissionDTO {
    private Long id;
    private String name;
    private String url;
    private String pricing;
    private String shortDescription;
    private String longDescription;
    /** Plural, because the column is: a tool is listed under every category it was submitted for. */
    private List<String> categories;
    private List<String> tags;
    private Boolean approved;
    private OffsetDateTime submittedAt;
}
