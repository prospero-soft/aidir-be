package ro.prospero.aidir.data;

import java.util.List;

/**
 * One page of the talent directory plus what it took to get there.
 *
 * <p>{@code total} is the count over the whole filtered set, not {@code items.size()}: the front end draws
 * numbered pages, which needs to know how many there are before fetching them.
 *
 * <p>The facet lists are what the sidebar renders its counts from. Each is counted against the other active
 * filters rather than its own, so no row ever reads (0) beside a value that has not been picked yet.
 */
public record TalentPage(List<TalentCardDTO> items,
                         long total,
                         int page,
                         int size,
                         List<FacetCount> workplaceFacets,
                         List<FacetCount> employmentFacets,
                         List<FacetCount> skillFacets,
                         List<FacetCount> languageFacets,
                         List<FacetCount> locationFacets) {
}
