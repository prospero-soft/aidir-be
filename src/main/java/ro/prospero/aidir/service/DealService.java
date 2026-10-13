package ro.prospero.aidir.service;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import ro.prospero.aidir.data.DealDTO;
import ro.prospero.aidir.data.DealPayload;
import ro.prospero.aidir.data.DealPayload.PlacementContent;
import ro.prospero.aidir.data.DealStatus;
import ro.prospero.aidir.data.DealSummaryDTO;
import ro.prospero.aidir.data.Placement;
import ro.prospero.aidir.exception.ApiPayloadValidationException;
import ro.prospero.aidir.jooq.generated.public_.tables.records.DealRecord;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static ro.prospero.aidir.jooq.generated.public_.Tables.DEAL;
import static ro.prospero.aidir.jooq.generated.public_.Tables.DEAL_CATEGORY;
import static ro.prospero.aidir.jooq.generated.public_.Tables.DEAL_PLACEMENT;

/**
 * A vendor's own deals: saving drafts, submitting them for review, and deleting the ones not yet approved.
 *
 * <p>Every query is scoped by the account as well as the id, so another vendor's deal answers exactly as a
 * deal that does not exist - a 404. Only draft and rejected deals can be changed; saving a rejected deal
 * makes it a draft again.
 *
 * <p>Transactions are jOOQ's, for the reason given on {@link VendorOnboardingService}. Logos are written
 * inside them, because their directory is named after the deal id, and removed again if the transaction
 * fails. Logos a save replaces are only deleted after it commits, so a failed save leaves the old ones.
 */
@Service
public class DealService {
    private static final String DEAL_DIR = "deals/";

    /**
     * Raster images only. The storage service would take an SVG too, but /media serves from our own origin
     * and a vendor-supplied SVG can carry script.
     */
    private static final Set<String> LOGO_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    private final DSLContext dslContext;
    private final DealRows dealRows;
    private final FileStorageService fileStorageService;

    public DealService(DSLContext dslContext, DealRows dealRows, FileStorageService fileStorageService) {
        this.dslContext = dslContext;
        this.dealRows = dealRows;
        this.fileStorageService = fileStorageService;
    }

    public List<DealSummaryDTO> list(long accountId) {
        return dealRows.read(dslContext, DEAL.ACCOUNT_ID.eq(accountId), DEAL.UPDATED_AT.desc())
                       .stream()
                       .map(stored -> summaryOf(stored.deal()))
                       .toList();
    }

    public DealDTO get(long accountId, long dealId) {
        return dealRows.readOne(dslContext, DEAL.ID.eq(dealId).and(DEAL.ACCOUNT_ID.eq(accountId)))
                       .map(DealRows.StoredDeal::deal)
                       .orElseThrow(() -> notFound(dealId));
    }

    /**
     * @param logos new logo files keyed by {@link Placement#wire()}; placements without one keep no logo
     */
    public DealDTO create(long accountId, DealPayload payload, Map<String, MultipartFile> logos) {
        checkPayload(payload, logos);

        List<String> storedPaths = new ArrayList<>();
        long dealId;
        try {
            dealId = dslContext.transactionResult(cfg -> {
                DSLContext tx = DSL.using(cfg);
                Long id = tx.insertInto(DEAL)
                            .set(DEAL.ACCOUNT_ID, accountId)
                            .set(DEAL.SPONSORED, payload.sponsored())
                            .set(DEAL.SPONSORED_LABEL, payload.sponsoredLabel())
                            .set(DEAL.CAMPAIGN, payload.campaign())
                            .set(DEAL.INTERNAL_CAMPAIGN_ID, payload.internalCampaignId())
                            .set(DEAL.LONG_DESCRIPTION, payload.longDescription())
                            .returningResult(DEAL.ID)
                            .fetchOne(DEAL.ID);
                if (id == null) {
                    throw new IllegalStateException("Creating the deal returned no id");
                }
                writePlacements(tx, id, payload, Map.of(), logos, storedPaths);
                return id;
            });
        } catch (RuntimeException exception) {
            storedPaths.forEach(fileStorageService::delete);
            throw exception;
        }
        return get(accountId, dealId);
    }

    /** Replaces the deal's content with the payload's, and makes it a draft. */
    public DealDTO update(long accountId, long dealId, DealPayload payload, Map<String, MultipartFile> logos) {
        checkPayload(payload, logos);

        List<String> storedPaths = new ArrayList<>();
        List<String> supersededPaths;
        try {
            supersededPaths = dslContext.transactionResult(cfg -> {
                DSLContext tx = DSL.using(cfg);
                requireEditable(lockOwned(tx, accountId, dealId));

                Map<String, String> previousLogos = dealRows.logoPaths(tx, dealId);
                tx.update(DEAL)
                  .set(DEAL.STATUS, DealStatus.DRAFT.wire())
                  .set(DEAL.SPONSORED, payload.sponsored())
                  .set(DEAL.SPONSORED_LABEL, payload.sponsoredLabel())
                  .set(DEAL.CAMPAIGN, payload.campaign())
                  .set(DEAL.INTERNAL_CAMPAIGN_ID, payload.internalCampaignId())
                  .set(DEAL.LONG_DESCRIPTION, payload.longDescription())
                  .setNull(DEAL.REJECTION_REASON)
                  .set(DEAL.UPDATED_AT, OffsetDateTime.now())
                  .where(DEAL.ID.eq(dealId))
                  .execute();
                tx.deleteFrom(DEAL_PLACEMENT).where(DEAL_PLACEMENT.DEAL_ID.eq(dealId)).execute();

                Set<String> keptPaths = writePlacements(tx, dealId, payload, previousLogos, logos, storedPaths);
                return previousLogos.values().stream().filter(path -> !keptPaths.contains(path)).toList();
            });
        } catch (RuntimeException exception) {
            storedPaths.forEach(fileStorageService::delete);
            throw exception;
        }
        supersededPaths.forEach(fileStorageService::delete);
        return get(accountId, dealId);
    }

    /**
     * Sends the deal to review once it is complete. What is checked is the deal as saved, not anything the
     * request carries: the form saves before it submits.
     */
    public DealDTO submit(long accountId, long dealId) {
        dslContext.transaction(cfg -> {
            DSLContext tx = DSL.using(cfg);
            requireEditable(lockOwned(tx, accountId, dealId));

            DealDTO deal = dealRows.readOne(tx, DEAL.ID.eq(dealId))
                                   .map(DealRows.StoredDeal::deal)
                                   .orElseThrow(() -> notFound(dealId));
            List<String> violations = DealSubmissionRules.check(deal.placements(), deal.longDescription(),
                                                                deal.perPlacement(), LocalDate.now());
            if (!violations.isEmpty()) {
                throw new ApiPayloadValidationException("This deal is not ready to submit yet.", violations);
            }

            OffsetDateTime now = OffsetDateTime.now();
            tx.update(DEAL)
              .set(DEAL.STATUS, DealStatus.PENDING.wire())
              .set(DEAL.SUBMITTED_AT, now)
              .set(DEAL.UPDATED_AT, now)
              .setNull(DEAL.REJECTION_REASON)
              .where(DEAL.ID.eq(dealId))
              .execute();
        });
        return get(accountId, dealId);
    }

    public void delete(long accountId, long dealId) {
        List<String> logoPaths = dslContext.transactionResult(cfg -> {
            DSLContext tx = DSL.using(cfg);
            requireEditable(lockOwned(tx, accountId, dealId));
            List<String> paths = List.copyOf(dealRows.logoPaths(tx, dealId).values());
            // The placements go with it: deal_placement cascades.
            tx.deleteFrom(DEAL).where(DEAL.ID.eq(dealId)).execute();
            return paths;
        });
        logoPaths.forEach(fileStorageService::delete);
    }

    /**
     * Inserts a row per chosen placement. A placement's logo is, in order: the new file uploaded for it, the
     * one it already had if the payload still names it, or none.
     *
     * @return the previous logo paths that are still in use
     */
    private Set<String> writePlacements(DSLContext tx, long dealId, DealPayload payload,
                                        Map<String, String> previousLogos, Map<String, MultipartFile> logos,
                                        List<String> storedPaths) {
        Set<String> keptPaths = new HashSet<>();
        for (Placement placement : new LinkedHashSet<>(payload.placements())) {
            PlacementContent content = placement.hasContent() ? payload.perPlacement().get(placement.wire()) : null;
            String logoPath = null;

            if (content != null) {
                MultipartFile logo = logos.get(placement.wire());
                if (logo != null && !logo.isEmpty()) {
                    logoPath = fileStorageService.storeImage(logo, DEAL_DIR + dealId, logoBaseName(placement));
                    storedPaths.add(logoPath);
                } else if (content.info().logoUrl() != null && previousLogos.containsKey(placement.wire())) {
                    logoPath = previousLogos.get(placement.wire());
                    keptPaths.add(logoPath);
                }
            }

            dealRows.insertPlacement(tx, dealId, placement, content, logoPath);
        }
        return keptPaths;
    }

    /** A fresh name per upload, so a replaced logo is never served from a cache under the old one's URL. */
    private static String logoBaseName(Placement placement) {
        return placement.wire() + "-logo-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * What Bean Validation cannot say about the payload: that its content and logos belong to placements
     * it chose, that the categories exist and that the logos are images we serve.
     */
    private void checkPayload(DealPayload payload, Map<String, MultipartFile> logos) {
        List<String> violations = new ArrayList<>();
        Set<String> chosen = new HashSet<>();
        payload.placements().forEach(placement -> chosen.add(placement.wire()));

        payload.perPlacement().keySet().forEach(key -> {
            boolean known = Placement.find(key).map(Placement::hasContent).orElse(false);
            if (!known) {
                violations.add("perPlacement." + key + ": not a placement with content");
            } else if (!chosen.contains(key)) {
                violations.add("perPlacement." + key + ": the placement is not chosen");
            }
        });

        logos.forEach((key, file) -> {
            if (!payload.perPlacement().containsKey(key)) {
                violations.add("logo-" + key + ": there is no content for this placement");
            } else if (file != null && !file.isEmpty() && !isLogoType(file)) {
                violations.add("logo-" + key + ": the logo must be a PNG, JPEG or WebP image");
            }
        });

        Set<String> categories = new HashSet<>(dslContext.fetchValues(DEAL_CATEGORY.NAME));
        payload.perPlacement().forEach((key, content) -> {
            String category = content.info().category();
            if (category != null && !categories.contains(category)) {
                violations.add("perPlacement." + key + ".info.category: unknown category '" + category + "'");
            }
        });

        if (!violations.isEmpty()) {
            throw new ApiPayloadValidationException("Validating the deal failed.", violations);
        }
    }

    private static boolean isLogoType(MultipartFile file) {
        String type = file.getContentType();
        return type != null && LOGO_TYPES.contains(type.toLowerCase(Locale.ROOT).trim());
    }

    /** Locked so two saves of the same deal - two tabs, a double click - apply one after the other. */
    private static DealRecord lockOwned(DSLContext tx, long accountId, long dealId) {
        DealRecord deal = tx.selectFrom(DEAL)
                            .where(DEAL.ID.eq(dealId))
                            .and(DEAL.ACCOUNT_ID.eq(accountId))
                            .forUpdate()
                            .fetchOne();
        if (deal == null) {
            throw notFound(dealId);
        }
        return deal;
    }

    private static void requireEditable(DealRecord deal) {
        DealStatus status = DealStatus.fromWire(deal.getStatus());
        if (!status.editable()) {
            String detail = status == DealStatus.PENDING
                    ? "This deal is waiting for review and can't be changed."
                    : "This deal has been approved and can't be changed.";
            throw new ResponseStatusException(HttpStatus.CONFLICT, detail);
        }
    }

    private static DealSummaryDTO summaryOf(DealDTO deal) {
        String title = deal.perPlacement().values().stream()
                           .map(content -> firstNonBlank(content.info().productName(), content.info().line1()))
                           .filter(Objects::nonNull)
                           .findFirst()
                           .orElse(null);
        return new DealSummaryDTO(deal.id(), deal.status(), title, deal.placements(), deal.rejectionReason(),
                                  deal.createdAt(), deal.updatedAt(), deal.submittedAt());
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static ResponseStatusException notFound(long dealId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "No deal with id " + dealId);
    }
}
