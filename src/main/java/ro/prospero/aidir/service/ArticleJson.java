package ro.prospero.aidir.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.JSONB;
import ro.prospero.aidir.data.ArticleBlock;
import ro.prospero.aidir.data.ArticleQuoteDTO;

import java.util.List;

/** The JSON that the submitted article and brief tables keep their lists, quotes and bodies in. */
final class ArticleJson {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {
    };
    private static final TypeReference<List<ArticleBlock>> BLOCKS = new TypeReference<>() {
    };

    private ArticleJson() {
    }

    static JSONB write(Object value) {
        try {
            return JSONB.valueOf(JSON_MAPPER.writeValueAsString(value));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise " + value, e);
        }
    }

    /** Written as a list of blocks, not of objects, so each block keeps the {@code kind} it is read back by. */
    static JSONB writeBody(List<ArticleBlock> body) {
        try {
            return JSONB.valueOf(JSON_MAPPER.writerFor(BLOCKS).writeValueAsString(body));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise " + body, e);
        }
    }

    static List<String> strings(JSONB jsonb) {
        return jsonb == null ? List.of() : read(jsonb, STRINGS, "string list");
    }

    static List<ArticleBlock> body(JSONB jsonb) {
        return read(jsonb, BLOCKS, "article body");
    }

    static ArticleQuoteDTO quote(JSONB jsonb) {
        try {
            return JSON_MAPPER.readValue(jsonb.data(), ArticleQuoteDTO.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Malformed quote: " + jsonb.data(), e);
        }
    }

    private static <T> T read(JSONB jsonb, TypeReference<T> type, String what) {
        try {
            return JSON_MAPPER.readValue(jsonb.data(), type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Malformed " + what + ": " + jsonb.data(), e);
        }
    }
}
