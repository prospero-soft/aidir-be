package ro.prospero.aidir.service;

import org.jooq.DSLContext;
import org.springframework.stereotype.Component;
import ro.prospero.aidir.data.ToolAttributes;
import ro.prospero.aidir.exception.ApiPayloadValidationException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static ro.prospero.aidir.jooq.generated.Tables.TOOL_ATTRIBUTE_OPTION;

/**
 * Refuses filter attributes outside {@code tool_attribute_option}. A value the directory does not offer would
 * be stored on a listing no filter could ever reach.
 */
@Component
public class ToolAttributeVocabulary {
    private final DSLContext dslContext;

    public ToolAttributeVocabulary(DSLContext dslContext) {
        this.dslContext = dslContext;
    }

    public void requireKnown(ToolAttributes attributes) {
        Map<String, Set<String>> vocabulary =
                dslContext.selectFrom(TOOL_ATTRIBUTE_OPTION)
                          .fetch()
                          .stream()
                          .collect(Collectors.groupingBy(
                                  row -> row.getAttribute(),
                                  Collectors.mapping(row -> row.getValue(), Collectors.toSet())));

        List<String> unknown = unknownValues(attributes, vocabulary);
        if (!unknown.isEmpty()) {
            throw new ApiPayloadValidationException("Validating the tool attributes failed.", unknown);
        }
    }

    static List<String> unknownValues(ToolAttributes attributes, Map<String, Set<String>> vocabulary) {
        Map<String, List<String>> submitted = new LinkedHashMap<>();
        submitted.put("platform", attributes.platforms());
        submitted.put("target_user", attributes.targetUsers());
        submitted.put("language", attributes.languages());
        submitted.put("ai_technology", attributes.aiTechnologies());
        submitted.put("deployment", attributes.deployments());
        submitted.put("industry", attributes.industries());

        List<String> unknown = new ArrayList<>();
        submitted.forEach((attribute, values) -> {
            Set<String> known = vocabulary.getOrDefault(attribute, Set.of());
            values.stream()
                  .filter(value -> !known.contains(value))
                  .forEach(value -> unknown.add(attribute + ": '" + value + "' is not a known value"));
        });
        return unknown;
    }
}
