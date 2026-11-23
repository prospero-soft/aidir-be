package ro.prospero.aidir.data;

import java.util.List;
import java.util.Map;

/**
 * One page of the directory plus what it took to get there.
 *
 * <p>{@code total} is the count over the whole filtered set, not {@code items.size()}: the front end draws
 * numbered pages, which needs to know how many there are before fetching them.
 *
 * @param facets the counts for each filter, keyed by {@link ToolFilter#param()}
 */
public record ToolPage(List<ToolCardDTO> items,
                       long total,
                       int page,
                       int size,
                       Map<String, List<FacetCount>> facets) {
}
