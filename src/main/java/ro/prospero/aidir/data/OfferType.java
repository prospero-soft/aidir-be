package ro.prospero.aidir.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** The {@code deal_offer_type} vocabulary. Which offer fields are required depends on it. */
public enum OfferType {
    PERCENTAGE("percentage"),
    FIXED("fixed"),
    LIFETIME("lifetime"),
    TRIAL("trial"),
    CREDITS("credits"),
    CUSTOM("custom");

    private final String wire;
    OfferType(String wire) { this.wire = wire; }

    @JsonValue public String wire() { return wire; }

    @JsonCreator
    public static OfferType fromWire(String v) {
        for (OfferType t : values()) if (t.wire.equals(v)) return t;
        throw new IllegalArgumentException("Unknown offer type: " + v);
    }
}
