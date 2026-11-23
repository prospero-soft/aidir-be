package ro.prospero.aidir.data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * One published listing, as its details page reads it: the directory row plus everything hanging off it
 * that a card has no room for.
 *
 * <p>The directory row is nested rather than restated - the details page shows the same name, blurb,
 * pricing, categories, tags and logo the card does, and one shape for both is what keeps them from
 * drifting. This sits beside {@link ToolDTO} rather than replacing it: that one is what the search index,
 * the upload event and the CMS exports are built from, and none of them has any use for pricing plans.
 *
 * <p>{@link Feature} mirrors the record in {@link VendorSubmissionPayload}, so what the wizard writes into the
 * jsonb column is what this reads back. It stays a separate type because that one describes what was
 * submitted and this one describes what is published.
 */
public record ToolDetailsDTO(ToolCardDTO card,
                             String longDescription,
                             List<Feature> features,
                             List<ToolPlan> plans,
                             String highlightPlanId,
                             List<String> integrations,
                             String demoVideoUrl,
                             List<String> screenshotUrls,
                             OffsetDateTime submittedAt,
                             List<ToolCardDTO> similar) {

    public record Feature(String id, String title, String description) {
    }
}
