package ro.prospero.aidir.data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * The signed-in vendor's own listing, as the dashboard's AI Tool Profile page reads and edits it.
 *
 * <p>It sits beside {@link ToolDetailsDTO} rather than reusing it: the public page has no use for the FAQ
 * editor's ids or the screenshot ids, and the vendor has no use for the alternatives strip. Features and plans
 * are the public page's records, since both read the same jsonb columns.
 */
public record VendorToolDTO(long id,
                            String name,
                            String url,
                            String pricing,
                            String shortDescription,
                            String longDescription,
                            List<String> categories,
                            List<String> tags,
                            String logoUrl,
                            List<ToolDetailsDTO.Feature> features,
                            List<ToolDetailsDTO.Plan> plans,
                            String freeTierPlanId,
                            String highlightPlanId,
                            List<String> integrations,
                            String demoVideoUrl,
                            List<Screenshot> screenshots,
                            List<Faq> faqs,
                            boolean published,
                            OffsetDateTime approvedAt,
                            OffsetDateTime updatedAt) {

    /** {@code id} is the stored path, which is what a media edit names to keep a screenshot. */
    public record Screenshot(String id, String url) {
    }

    public record Faq(String id, String question, String answer) {
    }
}
