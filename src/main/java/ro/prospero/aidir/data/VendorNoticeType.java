package ro.prospero.aidir.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * What a vendor is asked to act on from their dashboard. Each type has its own wording and link in aidir-fe
 * (src/components/vendor-dashboard/notices/noticeContent.ts), so one added here is added there too.
 */
public enum VendorNoticeType {
    /** Review sent an article back; the vendor is to revise and resubmit it. */
    ARTICLE_CHANGES_REQUESTED("article_changes_requested");

    private final String wire;
    VendorNoticeType(String wire) { this.wire = wire; }

    @JsonValue public String wire() { return wire; }

    @JsonCreator
    public static VendorNoticeType fromWire(String v) {
        for (VendorNoticeType t : values()) if (t.wire.equals(v)) return t;
        throw new IllegalArgumentException("Unknown vendor notice type: " + v);
    }
}
