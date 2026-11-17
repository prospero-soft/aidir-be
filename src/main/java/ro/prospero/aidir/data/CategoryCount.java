package ro.prospero.aidir.data;

/**
 * A category from the vocabulary, with how many published tools sit in it. Shaped like {@link FacetCount}
 * plus the description, so a reader of either can take {@code value} and {@code count} the same way.
 */
public record CategoryCount(String value, String description, long count) {
}
