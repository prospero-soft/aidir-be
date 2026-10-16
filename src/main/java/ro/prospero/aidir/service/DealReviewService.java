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

import static ro.prospero.aidir.jooq.generated.public_.Tables.DEAL;

/**
 * The admin's side of deals: the queue of submitted deals and the verdict on each. An approved deal goes
 * live on the dates its placements were scheduled for. Nothing is copied anywhere, because an approved deal
 * can no longer change.
 */
@Service
public class DealReviewService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DealReviewService.class);

    private final DSLContext dslContext;
    private final DealRows dealRows;
    private final DealService dealService;
    private final DevVendorAccounts devVendors;
    private final VendorContacts vendorContacts;

    public DealReviewService(DSLContext dslContext, DealRows dealRows, DealService dealService,
                             DevVendorAccounts devVendors, VendorContacts vendorContacts) {
        this.dslContext = dslContext;
        this.dealRows = dealRows;
        this.dealService = dealService;
        this.devVendors = devVendors;
        this.vendorContacts = vendorContacts;
    }

    /** The deals waiting for a verdict, oldest submission first. */
    public List<DealReviewDTO> queue() {
        List<DealRows.StoredDeal> pending = dealRows.read(dslContext,
                                                          DEAL.STATUS.eq(DealStatus.PENDING.wire()),
                                                          DEAL.SUBMITTED_AT.asc());
        if (pending.isEmpty()) {
            return List.of();
        }

        Map<Long, VendorContacts.Contact> vendors =
                vendorContacts.of(pending.stream().map(DealRows.StoredDeal::accountId).toList());

        return pending.stream().map(stored -> {
            VendorContacts.Contact vendor = VendorContacts.orUnknown(vendors, stored.accountId());
            return new DealReviewDTO(stored.deal(), stored.accountId(), vendor.name(), vendor.email());
        }).toList();
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
     * Save and Submit make, for the vendor {@link DevVendorAccounts#at} picks. Meant for development: the
     * admin has no deals of its own.
     */
    public DealDTO devSubmit(int vendorIndex, DealPayload payload, MultipartFile logo) {
        return createAndSubmit(devVendors.at(vendorIndex), payload, logo);
    }

    /**
     * Like {@link #devSubmit}, but for one vendor named by email, so a developer can sign in as that vendor
     * and find the deal under their own deals.
     */
    public DealDTO devSubmitAs(String vendorEmail, DealPayload payload, MultipartFile logo) {
        return createAndSubmit(devVendors.byEmail(vendorEmail), payload, logo);
    }

    private DealDTO createAndSubmit(long accountId, DealPayload payload, MultipartFile logo) {
        DealDTO draft = dealService.create(accountId, payload, logo);
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
