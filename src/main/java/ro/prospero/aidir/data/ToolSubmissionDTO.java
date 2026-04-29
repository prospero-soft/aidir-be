package ro.prospero.aidir.data;

import lombok.Data;

import java.util.List;

@Data
public class ToolSubmissionDTO {
    private String name;
    private String url;
    private String pricing;
    private String shortDescription;
    private String longDescription;
    private String category;
    private List<String> tags;


}
