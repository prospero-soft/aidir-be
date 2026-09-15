package ro.prospero.aidir.data;

import java.util.List;

/**
 * One page of the directory plus what it took to get there.
 *
 * <p>{@code total} is the count over the whole filtered set, not {@code items.size()}: the front end draws
 * numbered pages, which needs to know how many there are before fetching them.
 */
public record ToolPage(List<ToolCardDTO> items,
                       long total,
                       int page,
                       int size,
                       List<FacetCount> categoryFacets,
                       List<FacetCount> pricingFacets) {
}
