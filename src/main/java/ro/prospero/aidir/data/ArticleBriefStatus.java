package ro.prospero.aidir.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The {@code article_brief_status} vocabulary: triage, then as many rounds of drafting and client review as
 * the article takes. The schema's comment on the table has the transitions.
 */
public enum ArticleBriefStatus {
    RECEIVED("received"),
    DECLINED("declined"),
    ACCEPTED("accepted"),
    WRITING("writing"),
    CLIENT_REVIEW("client_review"),
    REVISIONS_REQUESTED("revisions_requested"),
    COMPLETED("completed");

    private final String wire;
    ArticleBriefStatus(String wire) { this.wire = wire; }

    @JsonValue public String wire() { return wire; }

    @JsonCreator
    public static ArticleBriefStatus fromWire(String v) {
        for (ArticleBriefStatus s : values()) if (s.wire.equals(v)) return s;
        throw new IllegalArgumentException("Unknown article brief status: " + v);
    }
}
