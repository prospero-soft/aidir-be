package ro.prospero.aidir.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** The {@code deal_status} vocabulary. Draft and rejected deals are the vendor's to edit, the rest are not. */
public enum DealStatus {
    DRAFT("draft"),
    PENDING("pending"),
    APPROVED("approved"),
    REJECTED("rejected");

    private final String wire;
    DealStatus(String wire) { this.wire = wire; }

    @JsonValue public String wire() { return wire; }

    public boolean editable() {
        return this == DRAFT || this == REJECTED;
    }

    @JsonCreator
    public static DealStatus fromWire(String v) {
        for (DealStatus s : values()) if (s.wire.equals(v)) return s;
        throw new IllegalArgumentException("Unknown deal status: " + v);
    }
}
