package ro.prospero.aidir.data;

import java.util.List;
import java.util.Objects;

/**
 * The optional answers behind the directory's multi-select filters. Values come from
 * {@code tool_attribute_option}; integrations are not here because they also allow the vendor's own entries.
 */
public record ToolAttributes(List<String> platforms,
                             List<String> targetUsers,
                             List<String> languages,
                             List<String> aiTechnologies,
                             List<String> deployments,
                             List<String> industries) {

    public ToolAttributes {
        platforms = clean(platforms);
        targetUsers = clean(targetUsers);
        languages = clean(languages);
        aiTechnologies = clean(aiTechnologies);
        deployments = clean(deployments);
        industries = clean(industries);
    }

    public static ToolAttributes none() {
        return new ToolAttributes(null, null, null, null, null, null);
    }

    private static List<String> clean(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                     .filter(Objects::nonNull)
                     .map(String::trim)
                     .filter(value -> !value.isEmpty())
                     .distinct()
                     .toList();
    }
}
