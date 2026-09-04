package ro.prospero.aidir.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Talent-side plan keys, mirroring UserPlanKey in aidir-fe (src/components/user-onboarding/types.ts).
 * Kept separate from {@link PlanKey}, which is the vendor-side ladder.
 */
public enum UserPlanKey {
    FREE("free"),
    VERIFIED("verified"),
    PRO("pro"),
    MARKETPLACE("marketplace");

    private final String wire;
    UserPlanKey(String wire) { this.wire = wire; }

    @JsonValue public String wire() { return wire; }

    @JsonCreator
    public static UserPlanKey fromWire(String v) {
        for (UserPlanKey k : values()) if (k.wire.equals(v)) return k;
        throw new IllegalArgumentException("Unknown user plan key: " + v);
    }
}
