package ro.prospero.aidir.service;

import org.junit.jupiter.api.Test;
import ro.prospero.aidir.data.ToolAttributes;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ToolAttributeVocabularyTest {

    private static final Map<String, Set<String>> VOCABULARY = Map.of("platform", Set.of("Web", "iOS"),
                                                                      "industry", Set.of("Legal"));

    @Test
    void acceptsNoAnswersAtAll() {
        assertThat(ToolAttributeVocabulary.unknownValues(ToolAttributes.none(), VOCABULARY)).isEmpty();
    }

    @Test
    void acceptsValuesFromTheirOwnVocabulary() {
        ToolAttributes attributes = new ToolAttributes(List.of("Web"), null, null, null, null, List.of("Legal"));

        assertThat(ToolAttributeVocabulary.unknownValues(attributes, VOCABULARY)).isEmpty();
    }

    @Test
    void namesAValueFromAnotherVocabulary() {
        ToolAttributes attributes = new ToolAttributes(List.of("Legal"), null, null, null, null, null);

        assertThat(ToolAttributeVocabulary.unknownValues(attributes, VOCABULARY))
                .containsExactly("platform: 'Legal' is not a known value");
    }

    @Test
    void cleansBlanksAndDuplicatesBeforeChecking() {
        ToolAttributes attributes = new ToolAttributes(List.of(" Web ", "Web", "  "), null, null, null, null, null);

        assertThat(attributes.platforms()).containsExactly("Web");
    }
}
