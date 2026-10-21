package ro.prospero.aidir.data;

import java.time.OffsetDateTime;

/**
 * Something the vendor is to act on: {@code subjectId} and {@code subject} name what it is about - for an
 * article, its id and headline - and {@code since} is when it became due. Mirrors VendorNotice in aidir-fe
 * (src/types/vendorNotice.ts).
 */
public record VendorNoticeDTO(VendorNoticeType type, long subjectId, String subject, OffsetDateTime since) {
}
