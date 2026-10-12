package ro.prospero.aidir.data;

/** A deal in the admin's review queue, with the vendor behind it. Mirrors DealReviewDTO in aidir-fe. */
public record DealReviewDTO(DealDTO deal,
                            long vendorId,
                            String vendorName,
                            String vendorEmail) {
}
