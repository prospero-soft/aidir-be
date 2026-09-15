package ro.prospero.aidir.data;

/**
 * How many tools a filter value would yield. Counted against the other active filters, so a sidebar built
 * out of these never offers a row that leads nowhere.
 */
public record FacetCount(String value, long count) {
}
