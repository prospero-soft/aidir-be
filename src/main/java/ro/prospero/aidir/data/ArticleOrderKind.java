package ro.prospero.aidir.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** The {@code article_order_kind} vocabulary: an article the vendor wrote, or a brief our editors write from. */
public enum ArticleOrderKind {
    SUBMITTED("submitted"),
    EDITORIAL("editorial");

    private final String wire;
    ArticleOrderKind(String wire) { this.wire = wire; }

    @JsonValue public String wire() { return wire; }

    @JsonCreator
    public static ArticleOrderKind fromWire(String v) {
        for (ArticleOrderKind k : values()) if (k.wire.equals(v)) return k;
        throw new IllegalArgumentException("Unknown article order kind: " + v);
    }
}
