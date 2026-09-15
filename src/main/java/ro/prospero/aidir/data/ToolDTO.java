package ro.prospero.aidir.data;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
public class ToolDTO {
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
