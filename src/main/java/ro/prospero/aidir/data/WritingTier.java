package ro.prospero.aidir.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** The {@code article_writing_tier} vocabulary: how long the article our editors write is. */
public enum WritingTier {
    SHORT("short"),
    STANDARD("standard"),
    EXTENDED("extended");

    private final String wire;
    WritingTier(String wire) { this.wire = wire; }

    @JsonValue public String wire() { return wire; }

    @JsonCreator
    public static WritingTier fromWire(String v) {
        for (WritingTier t : values()) if (t.wire.equals(v)) return t;
        throw new IllegalArgumentException("Unknown writing tier: " + v);
    }
}
