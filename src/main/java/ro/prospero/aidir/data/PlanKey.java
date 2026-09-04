package ro.prospero.aidir.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PlanKey {
    FREE("free"),
    VERIFIED("verified"),
    GROWTH("growth"),
    PREMIUM("premium");

    private final String wire;
    PlanKey(String wire) { this.wire = wire; }

    @JsonValue public String wire() { return wire; }

    @JsonCreator
    public static PlanKey fromWire(String v) {
        for (PlanKey k : values()) if (k.wire.equals(v)) return k;
        throw new IllegalArgumentException("Unknown plan key: " + v);
    }
}
