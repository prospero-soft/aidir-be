package ro.prospero.aidir.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ToolSearchHit {
    private Long id;
    private String name;
    private String shortDescription;
    private List<String> tags;
}
