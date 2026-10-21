package ro.prospero.aidir.service;

import org.jooq.DSLContext;
import org.springframework.stereotype.Component;
import ro.prospero.aidir.data.ArticleBriefStatus;
import ro.prospero.aidir.data.VendorNoticeDTO;
import ro.prospero.aidir.data.VendorNoticeType;

import java.util.List;

import static ro.prospero.aidir.jooq.generated.Tables.ARTICLE_BRIEF;
import static ro.prospero.aidir.jooq.generated.Tables.ARTICLE_BRIEF_DRAFT;

/** The vendor's briefs whose draft our editors sent them, each due from when it was sent. */
@Component
public class ArticleBriefNotices implements VendorNoticeSource {
    private final DSLContext dslContext;

    public ArticleBriefNotices(DSLContext dslContext) {
        this.dslContext = dslContext;
    }

    @Override
    public List<VendorNoticeDTO> notices(long accountId) {
        // The client has answered every earlier round, so the sent round without feedback is the one waiting.
        return dslContext.select(ARTICLE_BRIEF.ID, ARTICLE_BRIEF.HEADLINE, ARTICLE_BRIEF_DRAFT.SENT_AT)
                         .from(ARTICLE_BRIEF)
                         .join(ARTICLE_BRIEF_DRAFT)
                         .on(ARTICLE_BRIEF_DRAFT.ARTICLE_BRIEF_ID.eq(ARTICLE_BRIEF.ID))
                         .and(ARTICLE_BRIEF_DRAFT.SENT_AT.isNotNull())
                         .and(ARTICLE_BRIEF_DRAFT.FEEDBACK_AT.isNull())
                         .where(ARTICLE_BRIEF.ACCOUNT_ID.eq(accountId))
                         .and(ARTICLE_BRIEF.STATUS.eq(ArticleBriefStatus.CLIENT_REVIEW.wire()))
                         .fetch(row -> new VendorNoticeDTO(VendorNoticeType.BRIEF_DRAFT_READY,
                                                           row.value1(), row.value2(), row.value3()));
    }
}
