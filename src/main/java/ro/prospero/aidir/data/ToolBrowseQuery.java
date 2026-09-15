package ro.prospero.aidir.data;

import java.util.List;

/**
 * A browse request with every parameter already defaulted and clamped, so the service below never has to
 * decide what a missing page size or a negative page number means.
 */
public record ToolBrowseQuery(String query,
                              List<String> categories,
                              List<String> pricing,
                              ToolSort sort,
                              int page,
                              int size) {

    public static final int DEFAULT_SIZE = 10;
    public static final int MAX_SIZE = 50;

    public static ToolBrowseQuery of(String query,
                                     List<String> categories,
                                     List<String> pricing,
                                     ToolSort sort,
                                     Integer page,
                                     Integer size) {
        String normalizedQuery = query == null || query.isBlank() ? null : query.trim();

        // Relevance means nothing without a query to score against, so browsing the catalogue falls back
        // to newest rather than handing back an arbitrary order.
        ToolSort resolvedSort = sort == null || (sort == ToolSort.RELEVANCE && normalizedQuery == null)
                ? ToolSort.NEWEST
                : sort;

        return new ToolBrowseQuery(normalizedQuery,
                                   clean(categories),
                                   clean(pricing),
                                   resolvedSort,
                                   Math.max(0, page == null ? 0 : page),
                                   Math.clamp(size == null ? DEFAULT_SIZE : size, 1, MAX_SIZE));
    }

    public boolean hasQuery() {
        return query != null;
    }

    public int offset() {
        return page * size;
    }

    private static List<String> clean(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                     .filter(v -> v != null && !v.isBlank())
                     .map(String::trim)
                     .distinct()
                     .toList();
    }
}
