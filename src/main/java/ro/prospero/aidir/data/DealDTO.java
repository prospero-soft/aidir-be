package ro.prospero.aidir.data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * A whole deal, as the vendor edits it and the admin reviews it. The content is in the same shape the
 * form saves, so a draft round-trips; {@code info.logoUrl} is the public URL of the stored logo. Mirrors
 * DealDTO in aidir-fe (src/types/deal.ts).
 */
public record DealDTO(long id,
                      DealStatus status,
                      List<Placement> placements,
                      boolean sponsored,
                      String sponsoredLabel,
                      String campaign,
                      String internalCampaignId,
                      String longDescription,
                      Map<String, DealPayload.PlacementContent> perPlacement,
                      String rejectionReason,
                      OffsetDateTime createdAt,
                      OffsetDateTime updatedAt,
                      OffsetDateTime submittedAt,
                      OffsetDateTime reviewedAt) {
}
