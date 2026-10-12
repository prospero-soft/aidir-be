package ro.prospero.aidir.data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * A row of the vendor's own deal list. {@code title} is whatever names the deal first - a product name,
 * else a banner's first line - and null for a draft that has neither yet. Mirrors DealSummaryDTO in
 * aidir-fe (src/types/deal.ts).
 */
public record DealSummaryDTO(long id,
                             DealStatus status,
                             String title,
                             List<Placement> placements,
                             String rejectionReason,
                             OffsetDateTime createdAt,
                             OffsetDateTime updatedAt,
                             OffsetDateTime submittedAt) {
}
