package ro.prospero.aidir.service;

import org.jooq.DSLContext;
import org.springframework.stereotype.Component;
import ro.prospero.aidir.data.SubmittedArticleStatus;
import ro.prospero.aidir.data.VendorNoticeDTO;
import ro.prospero.aidir.data.VendorNoticeType;

import java.util.List;

import static ro.prospero.aidir.jooq.generated.Tables.SUBMITTED_ARTICLE;
import static ro.prospero.aidir.jooq.generated.Tables.SUBMITTED_ARTICLE_REVIEW_ROUND;

/** The vendor's articles review sent back, each due from when it was sent back. */
@Component
public class SubmittedArticleNotices implements VendorNoticeSource {
    private final DSLContext dslContext;

    public SubmittedArticleNotices(DSLContext dslContext) {
        this.dslContext = dslContext;
    }

    @Override
    public List<VendorNoticeDTO> notices(long accountId) {
        return dslContext.select(SUBMITTED_ARTICLE.ID, SUBMITTED_ARTICLE.HEADLINE,
                                 SUBMITTED_ARTICLE_REVIEW_ROUND.REQUESTED_AT)
                         .from(SUBMITTED_ARTICLE)
                         .join(SUBMITTED_ARTICLE_REVIEW_ROUND)
                         .on(SUBMITTED_ARTICLE_REVIEW_ROUND.SUBMITTED_ARTICLE_ID.eq(SUBMITTED_ARTICLE.ID))
                         .and(SUBMITTED_ARTICLE_REVIEW_ROUND.RESUBMITTED_AT.isNull())
                         .where(SUBMITTED_ARTICLE.ACCOUNT_ID.eq(accountId))
                         .and(SUBMITTED_ARTICLE.STATUS.eq(SubmittedArticleStatus.CHANGES_REQUESTED.wire()))
                         .fetch(row -> new VendorNoticeDTO(VendorNoticeType.ARTICLE_CHANGES_REQUESTED,
                                                           row.value1(), row.value2(), row.value3()));
    }
}
