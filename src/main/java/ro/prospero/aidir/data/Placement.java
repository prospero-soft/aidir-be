package ro.prospero.aidir.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Optional;

/**
 * The {@code deal_placement_kind} vocabulary, with the ids the creation form uses. Every placement carries
 * a schedule and an offer of its own, and lists the deal in the public directory while it runs.
 */
public enum Placement {
    SIDEBAR("sidebar"),
    STANDARD_LISTING("standard-listing"),
    TOP_CATEGORY("top-category"),
    HOMEPAGE("homepage"),
    SPOTLIGHTS("spotlights"),
    BANNER("banner");

    private final String wire;

    Placement(String wire) {
        this.wire = wire;
    }

    @JsonValue public String wire() { return wire; }

    public static Optional<Placement> find(String v) {
        for (Placement p : values()) if (p.wire.equals(v)) return Optional.of(p);
        return Optional.empty();
    }

    @JsonCreator
    public static Placement fromWire(String v) {
        return find(v).orElseThrow(() -> new IllegalArgumentException("Unknown placement: " + v));
    }
}
