package ro.prospero.aidir.service;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import ro.prospero.aidir.data.ArticleBriefDTO;
import ro.prospero.aidir.data.ArticleBriefPayload;
import ro.prospero.aidir.data.ArticleBriefReviewDTO;
import ro.prospero.aidir.data.ArticleBriefStatus;
import ro.prospero.aidir.jooq.generated.tables.records.ArticleBriefRecord;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static ro.prospero.aidir.jooq.generated.Tables.ARTICLE_BRIEF;

/**
 * Triage of the briefs vendors place: the queue of those just received, and whether we take each on. An
 * accepted brief goes to the writing desk ({@link ArticleBriefWritingService}) to be assigned an editor; a
 * declined one goes back to its vendor with the reason.
 */
@Service
public class ArticleBriefTriageService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArticleBriefTriageService.class);

    private final DSLContext dslContext;
    private final ArticleBriefRows rows;
    private final VendorContacts vendorContacts;
    private final ArticleBriefService briefService;
    private final DevVendorAccounts devVendors;

    public ArticleBriefTriageService(DSLContext dslContext, ArticleBriefRows rows, VendorContacts vendorContacts,
                                     ArticleBriefService briefService, DevVendorAccounts devVendors) {
        this.dslContext = dslContext;
        this.rows = rows;
        this.vendorContacts = vendorContacts;
        this.briefService = briefService;
        this.devVendors = devVendors;
    }

    /** The briefs waiting for triage, oldest first. */
    public List<ArticleBriefReviewDTO> queue() {
        List<ArticleBriefRows.Stored> received =
                rows.read(dslContext, ARTICLE_BRIEF.STATUS.eq(ArticleBriefStatus.RECEIVED.wire()),
                          ArticleBriefRows.DraftScope.ALL, ARTICLE_BRIEF.CREATED_AT.asc());
        if (received.isEmpty()) {
            return List.of();
        }

        Map<Long, VendorContacts.Contact> vendors =
                vendorContacts.of(received.stream().map(ArticleBriefRows.Stored::accountId).toList());

        return received.stream().map(stored -> {
            VendorContacts.Contact vendor = VendorContacts.orUnknown(vendors, stored.accountId());
            return new ArticleBriefReviewDTO(stored.brief(), stored.accountId(), vendor.name(), vendor.email(), null,
                                             false);
        }).toList();
    }

    /** Accepting twice is the same as accepting once; accepting a brief already past triage otherwise is a conflict. */
    public void accept(long briefId) {
        dslContext.transaction(cfg -> {
            DSLContext tx = DSL.using(cfg);
            ArticleBriefStatus status = ArticleBriefStatus.fromWire(lock(tx, briefId).getStatus());
            if (status == ArticleBriefStatus.ACCEPTED) {
                return;
            }
            requireReceived(status, briefId);

            tx.update(ARTICLE_BRIEF)
              .set(ARTICLE_BRIEF.STATUS, ArticleBriefStatus.ACCEPTED.wire())
              .set(ARTICLE_BRIEF.TRIAGED_AT, OffsetDateTime.now())
              .where(ARTICLE_BRIEF.ID.eq(briefId))
              .execute();
        });
    }

    public void decline(long briefId, String reason) {
        dslContext.transaction(cfg -> {
            DSLContext tx = DSL.using(cfg);
            requireReceived(ArticleBriefStatus.fromWire(lock(tx, briefId).getStatus()), briefId);

            tx.update(ARTICLE_BRIEF)
              .set(ARTICLE_BRIEF.STATUS, ArticleBriefStatus.DECLINED.wire())
              .set(ARTICLE_BRIEF.TRIAGED_AT, OffsetDateTime.now())
              .set(ARTICLE_BRIEF.DECLINE_REASON, reason.trim())
              .where(ARTICLE_BRIEF.ID.eq(briefId))
              .execute();
        });
    }

    /**
     * Accepts the queue one brief at a time through {@link #accept}. A brief that fails is logged and left
     * queued rather than stopping the sweep. Meant for development.
     */
    public void devAcceptAll() {
        List<Long> briefIds = dslContext.select(ARTICLE_BRIEF.ID)
                                        .from(ARTICLE_BRIEF)
                                        .where(ARTICLE_BRIEF.STATUS.eq(ArticleBriefStatus.RECEIVED.wire()))
                                        .orderBy(ARTICLE_BRIEF.CREATED_AT.asc())
                                        .fetch(ARTICLE_BRIEF.ID);
        for (Long briefId : briefIds) {
            try {
                accept(briefId);
            } catch (RuntimeException e) {
                LOGGER.warn("Could not accept article brief {} while accepting the queue", briefId, e);
            }
        }
    }

    /**
     * Places a brief for a vendor account through the call the vendor's own flow makes, so it is validated and
     * priced as theirs would be. {@code At} picks the vendor by position, {@code As} by email; see
     * {@link DevVendorAccounts}. Meant for development.
     */
    public ArticleBriefDTO devPlaceAt(int vendorIndex, ArticleBriefPayload payload, MultipartFile featuredImage) {
        return briefService.place(devVendors.at(vendorIndex), payload, featuredImage);
    }

    public ArticleBriefDTO devPlaceAs(String vendorEmail, ArticleBriefPayload payload, MultipartFile featuredImage) {
        return briefService.place(devVendors.byEmail(vendorEmail), payload, featuredImage);
    }

    private static ArticleBriefRecord lock(DSLContext tx, long briefId) {
        ArticleBriefRecord brief = tx.selectFrom(ARTICLE_BRIEF)
                                     .where(ARTICLE_BRIEF.ID.eq(briefId))
                                     .forUpdate()
                                     .fetchOne();
        if (brief == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No article brief with id " + briefId);
        }
        return brief;
    }

    private static void requireReceived(ArticleBriefStatus status, long briefId) {
        if (status != ArticleBriefStatus.RECEIVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                                              "Article brief " + briefId + " is " + status.wire()
                                              + ", not waiting for triage");
        }
    }
}
