package ro.prospero.aidir.service;

import org.jooq.DSLContext;
import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import ro.prospero.aidir.data.ArticleOrderDTO;
import ro.prospero.aidir.data.ArticleOrderKind;
import ro.prospero.aidir.data.ArticleOrderStatus;
import ro.prospero.aidir.data.ArticleRequestPayload;
import ro.prospero.aidir.data.ArticleReviewDTO;
import ro.prospero.aidir.data.ArticleSubmissionPayload;
import ro.prospero.aidir.jooq.generated.public_.tables.records.ArticleOrderRecord;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static ro.prospero.aidir.jooq.generated.public_.Tables.ARTICLE_ORDER;

/**
 * The editorial team's side of article orders: the queue of pending orders and the verdict on each.
 * Approving an order only records the verdict - publishing the article is not wired up yet.
 */
@Service
public class ArticleReviewService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArticleReviewService.class);

    private final DSLContext dslContext;
    private final ArticleOrderRows orderRows;
    private final VendorContacts vendorContacts;
    private final ArticleOrderService orderService;
    private final DevVendorAccounts devVendors;

    public ArticleReviewService(DSLContext dslContext, ArticleOrderRows orderRows, VendorContacts vendorContacts,
                                ArticleOrderService orderService, DevVendorAccounts devVendors) {
        this.dslContext = dslContext;
        this.orderRows = orderRows;
        this.vendorContacts = vendorContacts;
        this.orderService = orderService;
        this.devVendors = devVendors;
    }

    /** The orders waiting for a verdict, oldest first. */
    public List<ArticleReviewDTO> queue() {
        List<ArticleOrderRows.StoredOrder> pending =
                orderRows.read(dslContext, ARTICLE_ORDER.STATUS.eq(ArticleOrderStatus.PENDING.wire()),
                               ARTICLE_ORDER.CREATED_AT.asc());
        if (pending.isEmpty()) {
            return List.of();
        }

        Map<Long, VendorContacts.Contact> vendors =
                vendorContacts.of(pending.stream().map(ArticleOrderRows.StoredOrder::accountId).toList());

        return pending.stream().map(stored -> {
            VendorContacts.Contact vendor = VendorContacts.orUnknown(vendors, stored.accountId());
            return new ArticleReviewDTO(stored.order(), stored.accountId(), vendor.name(), vendor.email());
        }).toList();
    }

    /** Approving twice is the same as approving once; approving anything not pending is a conflict. */
    public void approve(long orderId) {
        dslContext.transaction(cfg -> {
            DSLContext tx = DSL.using(cfg);
            ArticleOrderStatus status = ArticleOrderStatus.fromWire(lock(tx, orderId).getStatus());
            if (status == ArticleOrderStatus.APPROVED) {
                return;
            }
            requirePending(status, orderId);

            tx.update(ARTICLE_ORDER)
              .set(ARTICLE_ORDER.STATUS, ArticleOrderStatus.APPROVED.wire())
              .set(ARTICLE_ORDER.REVIEWED_AT, OffsetDateTime.now())
              .where(ARTICLE_ORDER.ID.eq(orderId))
              .execute();
        });
    }

    public void reject(long orderId, String reason) {
        dslContext.transaction(cfg -> {
            DSLContext tx = DSL.using(cfg);
            requirePending(ArticleOrderStatus.fromWire(lock(tx, orderId).getStatus()), orderId);

            tx.update(ARTICLE_ORDER)
              .set(ARTICLE_ORDER.STATUS, ArticleOrderStatus.REJECTED.wire())
              .set(ARTICLE_ORDER.REVIEWED_AT, OffsetDateTime.now())
              .set(ARTICLE_ORDER.REJECTION_REASON, reason.trim())
              .where(ARTICLE_ORDER.ID.eq(orderId))
              .execute();
        });
    }

    /**
     * Approves the queue, or only its orders of {@code kind} when one is given, one order at a time through
     * {@link #approve}. An order that fails is logged and left queued rather than stopping the sweep. Meant for
     * development.
     */
    public void devApproveAll(ArticleOrderKind kind) {
        Condition pending = ARTICLE_ORDER.STATUS.eq(ArticleOrderStatus.PENDING.wire());
        if (kind != null) {
            pending = pending.and(ARTICLE_ORDER.KIND.eq(kind.wire()));
        }
        List<Long> orderIds = dslContext.select(ARTICLE_ORDER.ID)
                                        .from(ARTICLE_ORDER)
                                        .where(pending)
                                        .orderBy(ARTICLE_ORDER.CREATED_AT.asc())
                                        .fetch(ARTICLE_ORDER.ID);
        for (Long orderId : orderIds) {
            try {
                approve(orderId);
            } catch (RuntimeException e) {
                LOGGER.warn("Could not approve article order {} while approving the queue", orderId, e);
            }
        }
    }

    /**
     * Places an article order for a vendor account, through the same calls the vendor's own flows make, so it
     * is validated and priced as theirs would be. The {@code At} variants pick the vendor by position, the
     * {@code As} ones by email; see {@link DevVendorAccounts}. Meant for development.
     */
    public ArticleOrderDTO devSubmitArticleAt(int vendorIndex, ArticleSubmissionPayload payload,
                                              MultipartFile featuredImage, List<MultipartFile> additionalImages) {
        return orderService.submitArticle(devVendors.at(vendorIndex), payload, featuredImage, additionalImages);
    }

    public ArticleOrderDTO devSubmitArticleAs(String vendorEmail, ArticleSubmissionPayload payload,
                                              MultipartFile featuredImage, List<MultipartFile> additionalImages) {
        return orderService.submitArticle(devVendors.byEmail(vendorEmail), payload, featuredImage, additionalImages);
    }

    public ArticleOrderDTO devRequestArticleAt(int vendorIndex, ArticleRequestPayload payload,
                                               MultipartFile featuredImage) {
        return orderService.requestArticle(devVendors.at(vendorIndex), payload, featuredImage);
    }

    public ArticleOrderDTO devRequestArticleAs(String vendorEmail, ArticleRequestPayload payload,
                                               MultipartFile featuredImage) {
        return orderService.requestArticle(devVendors.byEmail(vendorEmail), payload, featuredImage);
    }

    private static ArticleOrderRecord lock(DSLContext tx, long orderId) {
        ArticleOrderRecord order = tx.selectFrom(ARTICLE_ORDER).where(ARTICLE_ORDER.ID.eq(orderId)).forUpdate().fetchOne();
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No article order with id " + orderId);
        }
        return order;
    }

    private static void requirePending(ArticleOrderStatus status, long orderId) {
        if (status != ArticleOrderStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                                              "Article order " + orderId + " is " + status.wire()
                                              + ", not waiting for review");
        }
    }
}
