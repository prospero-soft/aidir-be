package ro.prospero.aidir.service;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import ro.prospero.aidir.data.ArticleSubmissionPayload;
import ro.prospero.aidir.data.SubmittedArticleDTO;
import ro.prospero.aidir.data.SubmittedArticleReviewDTO;
import ro.prospero.aidir.data.SubmittedArticleStatus;
import ro.prospero.aidir.jooq.generated.tables.records.SubmittedArticleRecord;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static ro.prospero.aidir.jooq.generated.Tables.SUBMITTED_ARTICLE;
import static ro.prospero.aidir.jooq.generated.Tables.SUBMITTED_ARTICLE_REVIEW_ROUND;

/**
 * The compliance review of articles vendors wrote: the queue of those waiting, and the verdict on each. An
 * article either passes or goes back to its vendor with the issues to fix, opening a review round their
 * revision closes. Approving publishes the article to the news pages in the same transaction.
 */
@Service
public class SubmittedArticleReviewService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SubmittedArticleReviewService.class);

    private final DSLContext dslContext;
    private final SubmittedArticleRows rows;
    private final VendorContacts vendorContacts;
    private final SubmittedArticleService articleService;
    private final DevVendorAccounts devVendors;
    private final ArticlePublisher publisher;

    public SubmittedArticleReviewService(DSLContext dslContext, SubmittedArticleRows rows,
                                         VendorContacts vendorContacts, SubmittedArticleService articleService,
                                         DevVendorAccounts devVendors, ArticlePublisher publisher) {
        this.dslContext = dslContext;
        this.rows = rows;
        this.vendorContacts = vendorContacts;
        this.articleService = articleService;
        this.devVendors = devVendors;
        this.publisher = publisher;
    }

    /** The articles waiting for review, longest waiting first: a revision waits from when it came back. */
    public List<SubmittedArticleReviewDTO> queue() {
        List<SubmittedArticleRows.Stored> pending =
                rows.read(dslContext, SUBMITTED_ARTICLE.STATUS.eq(SubmittedArticleStatus.PENDING_REVIEW.wire()),
                          SUBMITTED_ARTICLE.SUBMITTED_AT.asc());
        if (pending.isEmpty()) {
            return List.of();
        }

        Map<Long, VendorContacts.Contact> vendors =
                vendorContacts.of(pending.stream().map(SubmittedArticleRows.Stored::accountId).toList());

        return pending.stream().map(stored -> {
            VendorContacts.Contact vendor = VendorContacts.orUnknown(vendors, stored.accountId());
            return new SubmittedArticleReviewDTO(stored.article(), stored.accountId(), vendor.name(), vendor.email());
        }).toList();
    }

    /**
     * Approves the article and publishes it. Approving twice is the same as approving once, and publishes an
     * approved article that is not yet published; approving anything not waiting for review is a conflict.
     */
    public void approve(long articleId) {
        dslContext.transaction(cfg -> {
            DSLContext tx = DSL.using(cfg);
            SubmittedArticleStatus status = SubmittedArticleStatus.fromWire(lock(tx, articleId).getStatus());
            if (status != SubmittedArticleStatus.APPROVED) {
                requirePendingReview(status, articleId);
                tx.update(SUBMITTED_ARTICLE)
                  .set(SUBMITTED_ARTICLE.STATUS, SubmittedArticleStatus.APPROVED.wire())
                  .set(SUBMITTED_ARTICLE.REVIEWED_AT, OffsetDateTime.now())
                  .where(SUBMITTED_ARTICLE.ID.eq(articleId))
                  .execute();
            }
            publisher.publishSubmitted(tx, articleId);
        });
    }

    /** Sends the article back to its vendor with {@code notes}: the compliance issues they are to fix. */
    public void requestChanges(long articleId, String notes) {
        dslContext.transaction(cfg -> {
            DSLContext tx = DSL.using(cfg);
            requirePendingReview(SubmittedArticleStatus.fromWire(lock(tx, articleId).getStatus()), articleId);

            OffsetDateTime now = OffsetDateTime.now();
            tx.update(SUBMITTED_ARTICLE)
              .set(SUBMITTED_ARTICLE.STATUS, SubmittedArticleStatus.CHANGES_REQUESTED.wire())
              .set(SUBMITTED_ARTICLE.REVIEWED_AT, now)
              .where(SUBMITTED_ARTICLE.ID.eq(articleId))
              .execute();
            tx.insertInto(SUBMITTED_ARTICLE_REVIEW_ROUND)
              .set(SUBMITTED_ARTICLE_REVIEW_ROUND.SUBMITTED_ARTICLE_ID, articleId)
              .set(SUBMITTED_ARTICLE_REVIEW_ROUND.NOTES, notes.trim())
              .set(SUBMITTED_ARTICLE_REVIEW_ROUND.REQUESTED_AT, now)
              .execute();
        });
    }

    /**
     * Approves the queue one article at a time through {@link #approve}. An article that fails is logged and
     * left queued rather than stopping the sweep. Meant for development.
     */
    public void devApproveAll() {
        List<Long> articleIds =
                dslContext.select(SUBMITTED_ARTICLE.ID)
                          .from(SUBMITTED_ARTICLE)
                          .where(SUBMITTED_ARTICLE.STATUS.eq(SubmittedArticleStatus.PENDING_REVIEW.wire()))
                          .orderBy(SUBMITTED_ARTICLE.SUBMITTED_AT.asc())
                          .fetch(SUBMITTED_ARTICLE.ID);
        for (Long articleId : articleIds) {
            try {
                approve(articleId);
            } catch (RuntimeException e) {
                LOGGER.warn("Could not approve submitted article {} while approving the queue", articleId, e);
            }
        }
    }

    /**
     * Submits an article for a vendor account through the call the vendor's own flow makes, so it is validated
     * and priced as theirs would be. {@code At} picks the vendor by position, {@code As} by email; see
     * {@link DevVendorAccounts}. Meant for development.
     */
    public SubmittedArticleDTO devSubmitAt(int vendorIndex, ArticleSubmissionPayload payload,
                                           MultipartFile featuredImage, List<MultipartFile> images) {
        return articleService.submit(devVendors.at(vendorIndex), payload, featuredImage, images);
    }

    public SubmittedArticleDTO devSubmitAs(String vendorEmail, ArticleSubmissionPayload payload,
                                           MultipartFile featuredImage, List<MultipartFile> images) {
        return articleService.submit(devVendors.byEmail(vendorEmail), payload, featuredImage, images);
    }

    private static SubmittedArticleRecord lock(DSLContext tx, long articleId) {
        SubmittedArticleRecord article = tx.selectFrom(SUBMITTED_ARTICLE)
                                           .where(SUBMITTED_ARTICLE.ID.eq(articleId))
                                           .forUpdate()
                                           .fetchOne();
        if (article == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No submitted article with id " + articleId);
        }
        return article;
    }

    private static void requirePendingReview(SubmittedArticleStatus status, long articleId) {
        if (status != SubmittedArticleStatus.PENDING_REVIEW) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                                              "Submitted article " + articleId + " is " + status.wire()
                                              + ", not waiting for review");
        }
    }
}
