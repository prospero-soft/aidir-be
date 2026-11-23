package ro.prospero.aidir.data;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A browse request with every parameter already defaulted and clamped, so the service below never has to
 * decide what a missing page size or a negative page number means.
 */
public record ToolBrowseQuery(String query,
                              Map<ToolFilter, List<String>> filters,
                              boolean freeTrial,
                              ToolSort sort,
                              int page,
                              int size) {

    public static final int DEFAULT_SIZE = 10;
    public static final int MAX_SIZE = 50;
    /** Names both the browse parameter and its facet. */
    public static final String FREE_TRIAL_PARAM = "freeTrial";

    /** @param params the request's parameters, of which those naming a {@link ToolFilter} and the trial toggle are read */
    public static ToolBrowseQuery of(String query,
                                     Map<String, List<String>> params,
                                     ToolSort sort,
                                     Integer page,
                                     Integer size) {
        String normalizedQuery = query == null || query.isBlank() ? null : query.trim();

        // Relevance means nothing without a query to score against, so browsing the catalogue falls back
        // to newest rather than handing back an arbitrary order.
        ToolSort resolvedSort = sort == null || (sort == ToolSort.RELEVANCE && normalizedQuery == null)
                ? ToolSort.NEWEST
                : sort;

        Map<ToolFilter, List<String>> filters = new EnumMap<>(ToolFilter.class);
        for (ToolFilter filter : ToolFilter.values()) {
            filters.put(filter, clean(params.get(filter.param())));
        }

        List<String> freeTrial = params.get(FREE_TRIAL_PARAM);

        return new ToolBrowseQuery(normalizedQuery,
                                   filters,
                                   freeTrial != null && freeTrial.contains("true"),
                                   resolvedSort,
                                   Math.max(0, page == null ? 0 : page),
                                   Math.clamp(size == null ? DEFAULT_SIZE : size, 1, MAX_SIZE));
    }

    public List<String> selected(ToolFilter filter) {
        return filters.get(filter);
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
