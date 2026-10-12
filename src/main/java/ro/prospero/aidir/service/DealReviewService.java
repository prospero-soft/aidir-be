package ro.prospero.aidir.service;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import ro.prospero.aidir.data.DealDTO;
import ro.prospero.aidir.data.DealPayload;
import ro.prospero.aidir.data.DealReviewDTO;
import ro.prospero.aidir.data.DealStatus;
import ro.prospero.aidir.jooq.generated.public_.tables.records.DealRecord;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static ro.prospero.aidir.jooq.generated.public_.Tables.ACCOUNT;
import static ro.prospero.aidir.jooq.generated.public_.Tables.COMPANY_INFORMATION;
import static ro.prospero.aidir.jooq.generated.public_.Tables.DEAL;

/**
 * The admin's side of deals: the queue of submitted deals and the verdict on each. An approved deal goes
 * live on the dates its placements were scheduled for. Nothing is copied anywhere, because an approved deal
 * can no longer change.
 */
@Service
public class DealReviewService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DealReviewService.class);
    private static final String VENDOR = "vendor";

    private final DSLContext dslContext;
    private final DealRows dealRows;
    private final DealService dealService;

    public DealReviewService(DSLContext dslContext, DealRows dealRows, DealService dealService) {
        this.dslContext = dslContext;
        this.dealRows = dealRows;
        this.dealService = dealService;
    }

    /** The deals waiting for a verdict, oldest submission first. */
    public List<DealReviewDTO> queue() {
        List<DealRows.StoredDeal> pending = dealRows.read(dslContext,
                                                          DEAL.STATUS.eq(DealStatus.PENDING.wire()),
                                                          DEAL.SUBMITTED_AT.asc());
        if (pending.isEmpty()) {
            return List.of();
        }

        // The company's official name where onboarding recorded one, the account's display name otherwise.
        Map<Long, Vendor> vendors =
                dslContext.select(ACCOUNT.ID, ACCOUNT.DISPLAY_NAME, COMPANY_INFORMATION.OFFICIAL_NAME, ACCOUNT.EMAIL)
                          .from(ACCOUNT)
                          .leftJoin(COMPANY_INFORMATION).on(COMPANY_INFORMATION.ACCOUNT_ID.eq(ACCOUNT.ID))
                          .where(ACCOUNT.ID.in(pending.stream().map(DealRows.StoredDeal::accountId).toList()))
                          .fetchMap(ACCOUNT.ID, r -> new Vendor(r.value3() != null ? r.value3() : r.value2(),
                                                                r.value4()));

        return pending.stream().map(stored -> {
            Vendor vendor = vendors.getOrDefault(stored.accountId(), new Vendor(null, null));
            return new DealReviewDTO(stored.deal(), stored.accountId(), vendor.name(), vendor.email());
        }).toList();
    }

    private record Vendor(String name, String email) {
    }

    /** Approving twice is the same as approving once; approving anything not pending is a conflict. */
    public void approve(long dealId) {
        dslContext.transaction(cfg -> {
            DSLContext tx = DSL.using(cfg);
            DealStatus status = DealStatus.fromWire(lock(tx, dealId).getStatus());
            if (status == DealStatus.APPROVED) {
                return;
            }
            requirePending(status, dealId);

            tx.update(DEAL)
              .set(DEAL.STATUS, DealStatus.APPROVED.wire())
              .set(DEAL.REVIEWED_AT, OffsetDateTime.now())
              .setNull(DEAL.REJECTION_REASON)
              .where(DEAL.ID.eq(dealId))
              .execute();
        });
    }

    public void reject(long dealId, String reason) {
        dslContext.transaction(cfg -> {
            DSLContext tx = DSL.using(cfg);
            requirePending(DealStatus.fromWire(lock(tx, dealId).getStatus()), dealId);

            tx.update(DEAL)
              .set(DEAL.STATUS, DealStatus.REJECTED.wire())
              .set(DEAL.REVIEWED_AT, OffsetDateTime.now())
              .set(DEAL.REJECTION_REASON, reason.trim())
              .where(DEAL.ID.eq(dealId))
              .execute();
        });
    }

    /**
     * Approves the whole queue, one deal at a time through {@link #approve}. A deal that fails is logged and
     * left queued rather than stopping the sweep. Meant for development.
     */
    public void devApproveAll() {
        List<Long> pending = dslContext.select(DEAL.ID)
                                       .from(DEAL)
                                       .where(DEAL.STATUS.eq(DealStatus.PENDING.wire()))
                                       .orderBy(DEAL.SUBMITTED_AT.asc())
                                       .fetch(DEAL.ID);
        for (Long dealId : pending) {
            try {
                approve(dealId);
            } catch (RuntimeException e) {
                LOGGER.warn("Could not approve deal {} while approving the queue", dealId, e);
            }
        }
    }

    /**
     * Creates and submits a deal on behalf of a vendor account, through the same calls the vendor's own
     * Save and Submit make. The vendor is picked by position, wrapping around, so a run of synthetic deals
     * spreads over every vendor there is. Meant for development: the admin has no deals of its own.
     */
    public DealDTO devSubmit(int vendorIndex, DealPayload payload, Map<String, MultipartFile> logos) {
        List<Long> vendors = dslContext.select(ACCOUNT.ID)
                                       .from(ACCOUNT)
                                       .where(ACCOUNT.ACCOUNT_TYPE.eq(VENDOR))
                                       .orderBy(ACCOUNT.ID.asc())
                                       .fetch(ACCOUNT.ID);
        if (vendors.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                              "There are no vendor accounts to submit deals for. "
                                              + "Push vendor onboarding test data first.");
        }

        long accountId = vendors.get(Math.floorMod(vendorIndex, vendors.size()));
        DealDTO draft = dealService.create(accountId, payload, logos);
        return dealService.submit(accountId, draft.id());
    }

    private static DealRecord lock(DSLContext tx, long dealId) {
        DealRecord deal = tx.selectFrom(DEAL).where(DEAL.ID.eq(dealId)).forUpdate().fetchOne();
        if (deal == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No deal with id " + dealId);
        }
        return deal;
    }

    private static void requirePending(DealStatus status, long dealId) {
        if (status != DealStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                                              "Deal " + dealId + " is " + status.wire() + ", not waiting for review");
        }
    }
}
