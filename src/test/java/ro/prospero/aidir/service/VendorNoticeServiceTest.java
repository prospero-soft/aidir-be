package ro.prospero.aidir.service;

import org.junit.jupiter.api.Test;
import ro.prospero.aidir.data.VendorNoticeDTO;
import ro.prospero.aidir.data.VendorNoticeType;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VendorNoticeServiceTest {

    private static VendorNoticeDTO notice(long subjectId, String since) {
        return new VendorNoticeDTO(VendorNoticeType.ARTICLE_CHANGES_REQUESTED, subjectId, "Article " + subjectId,
                                   OffsetDateTime.parse(since));
    }

    @Test
    void everySourceIsAskedForTheAccountAndTheNewestNoticeComesFirst() {
        VendorNoticeSource first = accountId -> List.of(notice(1, "2026-10-01T00:00:00Z"));
        VendorNoticeSource second = accountId -> accountId == 7
                ? List.of(notice(2, "2026-10-03T00:00:00Z"), notice(3, "2026-10-02T00:00:00Z"))
                : List.of();

        List<VendorNoticeDTO> notices = new VendorNoticeService(List.of(first, second)).notices(7);

        assertThat(notices).extracting(VendorNoticeDTO::subjectId).containsExactly(2L, 3L, 1L);
    }

    @Test
    void noSourcesMeansNoNotices() {
        assertThat(new VendorNoticeService(List.of()).notices(7)).isEmpty();
    }
}
