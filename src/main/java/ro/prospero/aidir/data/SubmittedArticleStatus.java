package ro.prospero.aidir.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The {@code submitted_article_status} vocabulary. A submitted article waits for compliance review, and is
 * either approved or sent back with notes for the vendor to revise and resubmit.
 */
public enum SubmittedArticleStatus {
    PENDING_REVIEW("pending_review"),
    CHANGES_REQUESTED("changes_requested"),
    APPROVED("approved");

    private final String wire;
    SubmittedArticleStatus(String wire) { this.wire = wire; }

    @JsonValue public String wire() { return wire; }

    @JsonCreator
    public static SubmittedArticleStatus fromWire(String v) {
        for (SubmittedArticleStatus s : values()) if (s.wire.equals(v)) return s;
        throw new IllegalArgumentException("Unknown submitted article status: " + v);
    }
}
