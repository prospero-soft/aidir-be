package ro.prospero.aidir.data;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * A row of the vendor's own deal list. {@code title} is whatever names the deal first - a product name,
 * else a banner's first line - and null for a draft that has neither yet. {@code schedules} has one entry
 * per placement with a start date, so the dashboard can tell a running deal from a scheduled or ended one.
 * Mirrors DealSummaryDTO in aidir-fe (src/types/deal.ts).
 */
public record DealSummaryDTO(long id,
                             DealStatus status,
                             String title,
                             List<Placement> placements,
                             List<PlacementSchedule> schedules,
                             String campaign,
                             String rejectionReason,
                             OffsetDateTime createdAt,
                             OffsetDateTime updatedAt,
                             OffsetDateTime submittedAt,
                             OffsetDateTime reviewedAt) {

    /** {@code endDate} is the last day the placement runs, as the deal_placement table computes it. */
    public record PlacementSchedule(Placement placement, LocalDate startDate, LocalDate endDate) {
    }
}
