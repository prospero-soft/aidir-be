package ro.prospero.aidir.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Optional;

/**
 * The {@code deal_placement_kind} vocabulary, with the ids the creation form uses. Only the placements with
 * content carry a schedule, product details and an offer of their own.
 */
public enum Placement {
    SIDEBAR("sidebar", false),
    STANDARD_LISTING("standard-listing", false),
    TOP_CATEGORY("top-category", true),
    HOMEPAGE("homepage", true),
    SPOTLIGHTS("spotlights", true),
    BANNER("banner", true);

    private final String wire;
    private final boolean hasContent;

    Placement(String wire, boolean hasContent) {
        this.wire = wire;
        this.hasContent = hasContent;
    }

    @JsonValue public String wire() { return wire; }

    public boolean hasContent() { return hasContent; }

    /** Top Category and Homepage share one set of fields: the deal card. */
    public boolean isListing() {
        return this == TOP_CATEGORY || this == HOMEPAGE;
    }

    public static Optional<Placement> find(String v) {
        for (Placement p : values()) if (p.wire.equals(v)) return Optional.of(p);
        return Optional.empty();
    }

    @JsonCreator
    public static Placement fromWire(String v) {
        return find(v).orElseThrow(() -> new IllegalArgumentException("Unknown placement: " + v));
    }
}
