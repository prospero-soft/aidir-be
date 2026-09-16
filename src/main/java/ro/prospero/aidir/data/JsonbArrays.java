package ro.prospero.aidir.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.JSONB;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the {@code jsonb} string arrays - tags, categories - that the tool tables store.
 *
 * <p>Static and unconfigured on purpose: parsing {@code ["a","b"]} needs nothing from the application's
 * ObjectMapper, and the alternative is every caller that touches a tool row holding one of its own.
 */
public final class JsonbArrays {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonbArrays() {
    }

    /** An absent column is an empty list, not null: nothing downstream has to null-check a list of tags. */
    public static List<String> readStringArray(JSONB jsonb) {
        if (jsonb == null) {
            return List.of();
        }
        try {
            return MAPPER.readValue(jsonb.data(), new TypeReference<ArrayList<String>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Malformed JSON array in the tool table: " + jsonb.data(), e);
        }
    }
}
