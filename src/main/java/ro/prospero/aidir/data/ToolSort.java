package ro.prospero.aidir.data;

public enum ToolSort {
    /** Lucene score. Falls back to NEWEST when there is no query text to score against. */
    RELEVANCE,
    NEWEST,
    PRICE
}
