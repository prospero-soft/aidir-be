package ro.prospero.aidir.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** The {@code article_order_status} vocabulary. An order is placed pending; there are no drafts. */
public enum ArticleOrderStatus {
    PENDING("pending"),
    APPROVED("approved"),
    REJECTED("rejected");

    private final String wire;
    ArticleOrderStatus(String wire) { this.wire = wire; }

    @JsonValue public String wire() { return wire; }

    @JsonCreator
    public static ArticleOrderStatus fromWire(String v) {
        for (ArticleOrderStatus s : values()) if (s.wire.equals(v)) return s;
        throw new IllegalArgumentException("Unknown article order status: " + v);
    }
}
