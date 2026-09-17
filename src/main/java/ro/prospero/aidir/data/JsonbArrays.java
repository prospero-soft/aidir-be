package ro.prospero.aidir.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.JSONB;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the {@code jsonb} arrays - tags, categories, features, plans - that the tool tables store.
 *
 * <p>Static on purpose: parsing these needs nothing from the application's ObjectMapper, and the
 * alternative is every caller that touches a tool row holding one of its own. Unknown properties are
 * ignored rather than fatal - these columns hold what the onboarding wizard serialised, and a field added
 * to the form must not turn every existing row into a failed details page.
 */
public final class JsonbArrays {
    private static final ObjectMapper MAPPER =
            new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private JsonbArrays() {
    }

    /** An absent column is an empty list, not null: nothing downstream has to null-check a list of tags. */
    public static List<String> readStringArray(JSONB jsonb) {
        return read(jsonb, new TypeReference<ArrayList<String>>() {
        });
    }

    /**
     * A one-element JSON array, {@code ["Design"]}, for the containment predicates that ask whether a
     * stored array holds a value. Built through the mapper rather than by concatenation so that a category
     * name carrying a quote does not produce invalid JSON.
     */
    public static JSONB jsonArrayOf(String value) {
        try {
            return JSONB.valueOf(MAPPER.writeValueAsString(List.of(value)));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not build a JSON array out of " + value, e);
        }
    }

    /**
     * The same, for the arrays of objects: {@code features} and {@code plans}.
     *
     * @param type the list type to read into, e.g. {@code new TypeReference<ArrayList<Plan>>() {}}
     */
    public static <T> List<T> read(JSONB jsonb, TypeReference<? extends List<T>> type) {
        if (jsonb == null) {
            return List.of();
        }
        try {
            return MAPPER.readValue(jsonb.data(), type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Malformed JSON array in the tool table: " + jsonb.data(), e);
        }
    }
}
