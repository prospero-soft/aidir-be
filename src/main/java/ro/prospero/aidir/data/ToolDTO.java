package ro.prospero.aidir.data;

import lombok.Data;

import java.time.Instant;
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
    private String category;
    private List<String> tags;
    private Boolean approved;
    private OffsetDateTime submittedAt;
}
