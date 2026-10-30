package ro.prospero.aidir.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import ro.prospero.aidir.data.JsonbArrays;
import ro.prospero.aidir.data.ToolDetailsDTO;
import ro.prospero.aidir.data.VendorToolDTO;
import ro.prospero.aidir.data.VendorToolEdit;
import ro.prospero.aidir.event.ToolUploadEventPublisher;
import ro.prospero.aidir.exception.ApiPayloadValidationException;
import ro.prospero.aidir.jooq.generated.tables.records.ToolRecord;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static ro.prospero.aidir.jooq.generated.Tables.TOOL;
import static ro.prospero.aidir.jooq.generated.Tables.TOOL_IMAGE_METADATA;
import static ro.prospero.aidir.jooq.generated.Tables.TOOL_SUBMISSION_IMAGE_METADATA;

/**
 * The vendor's own listing, read and edited from the dashboard's AI Tool Profile page.
 *
 * <p>Only the listed copy in {@code tool} is edited; the submission it was approved from stays as it was
 * submitted. A vendor with more than one listing gets the most recently approved.
 *
 * <p>Each write answers with the listing as it now reads, so the page can redraw without asking again. The
 * read happens after the transaction commits; transactions are jOOQ's for the reason
 * {@link VendorOnboardingService} gives. Edits to what the search index holds - name, descriptions,
 * categories, tags, pricing - re-index the listing after the commit, as {@link ToolSubmissionService#approve}
 * indexes it.
 */
@Service
public class VendorToolService {
    private static final String LOGO = "logo";
    private static final String UPLOADS_AREA = "tools";

    private static final TypeReference<ArrayList<ToolDetailsDTO.Feature>> FEATURES = new TypeReference<>() {
    };
    private static final TypeReference<ArrayList<ToolDetailsDTO.Plan>> PLANS = new TypeReference<>() {
    };
    private static final TypeReference<ArrayList<VendorToolDTO.Faq>> FAQS = new TypeReference<>() {
    };

    private final DSLContext dslContext;
    private final ObjectMapper objectMapper;
    private final MediaUrls mediaUrls;
    private final FileStorageService fileStorageService;
    private final ToolService toolService;
    private final ToolUploadEventPublisher toolUploadEventPublisher;

    public VendorToolService(DSLContext dslContext,
                             ObjectMapper objectMapper,
                             MediaUrls mediaUrls,
                             FileStorageService fileStorageService,
                             ToolService toolService,
                             ToolUploadEventPublisher toolUploadEventPublisher) {
        this.dslContext = dslContext;
        this.objectMapper = objectMapper;
        this.mediaUrls = mediaUrls;
        this.fileStorageService = fileStorageService;
        this.toolService = toolService;
        this.toolUploadEventPublisher = toolUploadEventPublisher;
    }

    public Optional<VendorToolDTO> find(long vendorId) {
        return dslContext.selectFrom(TOOL)
                         .where(TOOL.ACCOUNT_ID.eq(vendorId))
                         .orderBy(TOOL.APPROVED_AT.desc(), TOOL.ID.desc())
                         .limit(1)
                         .fetchOptional()
                         .map(this::toDto);
    }

    public VendorToolDTO updateGeneral(long vendorId, VendorToolEdit.General general) {
        return write(vendorId, true, (tx, tool) -> {
            tx.update(TOOL)
              .set(TOOL.NAME, general.name().trim())
              .set(TOOL.URL, general.url().trim())
              .set(TOOL.SHORT_DESCRIPTION, general.shortDescription().trim())
              .set(TOOL.CATEGORIES, toJsonb(general.categories()))
              .set(TOOL.TAGS, toJsonb(general.tags()))
              .where(TOOL.ID.eq(tool.getId()))
              .execute();
            return List.of();
        });
    }

    public VendorToolDTO updateOverview(long vendorId, VendorToolEdit.Overview overview) {
        return write(vendorId, true, (tx, tool) -> {
            tx.update(TOOL)
              .set(TOOL.LONG_DESCRIPTION, overview.longDescription().trim())
              .where(TOOL.ID.eq(tool.getId()))
              .execute();
            return List.of();
        });
    }

    public VendorToolDTO updateFeatures(long vendorId, VendorToolEdit.Features features) {
        return write(vendorId, false, (tx, tool) -> {
            tx.update(TOOL)
              .set(TOOL.FEATURES, toJsonb(features.features()))
              .where(TOOL.ID.eq(tool.getId()))
              .execute();
            return List.of();
        });
    }

    public VendorToolDTO updateIntegrations(long vendorId, VendorToolEdit.Integrations integrations) {
        return write(vendorId, false, (tx, tool) -> {
            tx.update(TOOL)
              .set(TOOL.INTEGRATIONS, toJsonb(integrations.integrations()))
              .where(TOOL.ID.eq(tool.getId()))
              .execute();
            return List.of();
        });
    }

    /** The directory's free / freemium / paid filter reads {@code pricing}, so it is re-derived from the plans. */
    public VendorToolDTO updatePricing(long vendorId, VendorToolEdit.Pricing pricing) {
        String reference = ToolPricing.of(pricing.plans(), pricing.freeTierPlanId(),
                                          VendorToolEdit.Plan::id, VendorToolEdit.Plan::price);
        return write(vendorId, true, (tx, tool) -> {
            tx.update(TOOL)
              .set(TOOL.PLANS, toJsonb(pricing.plans()))
              .set(TOOL.FREE_TIER_PLAN_ID, pricing.freeTierPlanId())
              .set(TOOL.HIGHLIGHT_PLAN_ID, pricing.highlightPlanId())
              .set(TOOL.PRICING, reference)
              .where(TOOL.ID.eq(tool.getId()))
              .execute();
            return List.of();
        });
    }

    public VendorToolDTO updateFaqs(long vendorId, VendorToolEdit.Faqs faqs) {
        return write(vendorId, false, (tx, tool) -> {
            tx.update(TOOL)
              .set(TOOL.FAQS, toJsonb(faqs.faqs()))
              .where(TOOL.ID.eq(tool.getId()))
              .execute();
            return List.of();
        });
    }

    public VendorToolDTO replaceLogo(long vendorId, MultipartFile logo) {
        if (logo == null) {
            throw new ApiPayloadValidationException("No logo was uploaded.", List.of("logo: a file is required"));
        }
        StagedImages staged = new StagedImages(fileStorageService, UPLOADS_AREA);
        String path = staged.store(logo, LOGO);

        return writeStaged(vendorId, false, staged, (tx, tool) -> {
            List<String> previous = imagePaths(tx, tool.getId(), LOGO);
            tx.deleteFrom(TOOL_IMAGE_METADATA)
              .where(TOOL_IMAGE_METADATA.TOOL_ID.eq(tool.getId()))
              .and(TOOL_IMAGE_METADATA.KIND.eq(LOGO))
              .execute();
            insertImage(tx, tool.getId(), path, LOGO, 0);
            return previous;
        });
    }

    /**
     * Keeps the named screenshots in the order given, appends the uploads after them, and drops the rest.
     * Ids the listing does not have are ignored rather than refused: the page can only name what it was sent.
     */
    public VendorToolDTO updateMedia(long vendorId, VendorToolEdit.Media media, List<MultipartFile> uploads) {
        List<MultipartFile> files = uploads == null ? List.of()
                                                    : uploads.stream().filter(f -> f != null && !f.isEmpty()).toList();
        StagedImages staged = new StagedImages(fileStorageService, UPLOADS_AREA);
        List<String> added;
        try {
            added = files.stream().map(file -> staged.store(file, ToolService.SCREENSHOT)).toList();
        } catch (RuntimeException e) {
            // A refused second file must not leave the first behind.
            staged.discard();
            throw e;
        }

        return writeStaged(vendorId, false, staged, (tx, tool) -> {
            List<String> current = imagePaths(tx, tool.getId(), ToolService.SCREENSHOT);
            Set<String> kept = new LinkedHashSet<>(media.keepScreenshotIds());
            kept.retainAll(current);

            if (kept.size() + added.size() > VendorToolEdit.MAX_SCREENSHOTS) {
                throw new ApiPayloadValidationException(
                        "Too many screenshots.",
                        List.of("screenshots: at most " + VendorToolEdit.MAX_SCREENSHOTS + " are allowed"));
            }

            tx.deleteFrom(TOOL_IMAGE_METADATA)
              .where(TOOL_IMAGE_METADATA.TOOL_ID.eq(tool.getId()))
              .and(TOOL_IMAGE_METADATA.KIND.eq(ToolService.SCREENSHOT))
              .execute();
            int order = 0;
            for (String path : kept) {
                insertImage(tx, tool.getId(), path, ToolService.SCREENSHOT, order++);
            }
            for (String path : added) {
                insertImage(tx, tool.getId(), path, ToolService.SCREENSHOT, order++);
            }

            String demo = media.demoVideoUrl() == null || media.demoVideoUrl().isBlank()
                          ? null : media.demoVideoUrl().trim();
            tx.update(TOOL)
              .set(TOOL.DEMO_VIDEO_URL, demo)
              .where(TOOL.ID.eq(tool.getId()))
              .execute();

            return current.stream().filter(path -> !kept.contains(path)).toList();
        });
    }

    /** One section's change, answering with the image paths it stopped referring to. */
    @FunctionalInterface
    private interface Change {
        List<String> apply(DSLContext tx, ToolRecord tool);
    }

    private VendorToolDTO write(long vendorId, boolean reindex, Change change) {
        return writeStaged(vendorId, reindex, null, change);
    }

    /**
     * @param staged files stored ahead of the transaction, removed again if it fails; null when there are none
     */
    private VendorToolDTO writeStaged(long vendorId, boolean reindex, StagedImages staged, Change change) {
        List<String> released = new ArrayList<>();
        long toolId;
        try {
            toolId = dslContext.transactionResult(cfg -> {
                DSLContext tx = DSL.using(cfg);
                // Locked so two tabs saving at once apply one after the other rather than interleaving.
                ToolRecord tool = tx.selectFrom(TOOL)
                                    .where(TOOL.ACCOUNT_ID.eq(vendorId))
                                    .orderBy(TOOL.APPROVED_AT.desc(), TOOL.ID.desc())
                                    .limit(1)
                                    .forUpdate()
                                    .fetchOptional()
                                    .orElseThrow(() -> notListed(vendorId));
                released.addAll(change.apply(tx, tool));
                tx.update(TOOL)
                  .set(TOOL.UPDATED_AT, OffsetDateTime.now())
                  .where(TOOL.ID.eq(tool.getId()))
                  .execute();
                return tool.getId();
            });
        } catch (RuntimeException e) {
            if (staged != null) {
                staged.discard();
            }
            throw e;
        }

        deleteUnreferenced(released);
        if (reindex) {
            toolService.find(toolId).ifPresent(toolUploadEventPublisher::publishUpdate);
        }
        return dslContext.selectFrom(TOOL)
                         .where(TOOL.ID.eq(toolId))
                         .fetchOptional()
                         .map(this::toDto)
                         .orElseThrow(() -> notListed(vendorId));
    }

    /**
     * Approval copies image paths rather than files, so a path dropped here may still be the submission's
     * evidence. Only files nothing refers to any more are removed.
     */
    private void deleteUnreferenced(List<String> paths) {
        for (String path : paths) {
            boolean stillUsed =
                    dslContext.fetchExists(TOOL_IMAGE_METADATA, TOOL_IMAGE_METADATA.IMAGE_PATH.eq(path))
                    || dslContext.fetchExists(TOOL_SUBMISSION_IMAGE_METADATA,
                                              TOOL_SUBMISSION_IMAGE_METADATA.IMAGE_PATH.eq(path));
            if (!stillUsed) {
                fileStorageService.delete(path);
            }
        }
    }

    private static List<String> imagePaths(DSLContext tx, long toolId, String kind) {
        return tx.select(TOOL_IMAGE_METADATA.IMAGE_PATH)
                 .from(TOOL_IMAGE_METADATA)
                 .where(TOOL_IMAGE_METADATA.TOOL_ID.eq(toolId))
                 .and(TOOL_IMAGE_METADATA.KIND.eq(kind))
                 .orderBy(TOOL_IMAGE_METADATA.DISPLAY_ORDER.asc())
                 .fetch(TOOL_IMAGE_METADATA.IMAGE_PATH);
    }

    private static void insertImage(DSLContext tx, long toolId, String path, String kind, int order) {
        tx.insertInto(TOOL_IMAGE_METADATA)
          .set(TOOL_IMAGE_METADATA.TOOL_ID, toolId)
          .set(TOOL_IMAGE_METADATA.IMAGE_PATH, path)
          .set(TOOL_IMAGE_METADATA.KIND, kind)
          .set(TOOL_IMAGE_METADATA.DISPLAY_ORDER, order)
          .execute();
    }

    private VendorToolDTO toDto(ToolRecord tool) {
        long id = tool.getId();
        List<VendorToolDTO.Screenshot> screenshots =
                imagePaths(dslContext, id, ToolService.SCREENSHOT).stream()
                                                                  .map(path -> new VendorToolDTO.Screenshot(
                                                                          path, mediaUrls.publicUrl(path)))
                                                                  .toList();

        return new VendorToolDTO(id,
                                 tool.getName(),
                                 tool.getUrl(),
                                 tool.getPricing(),
                                 tool.getShortDescription(),
                                 tool.getLongDescription(),
                                 JsonbArrays.readStringArray(tool.getCategories()),
                                 JsonbArrays.readStringArray(tool.getTags()),
                                 toolService.findLogoUrls(List.of(id)).get(id),
                                 JsonbArrays.read(tool.getFeatures(), FEATURES),
                                 JsonbArrays.read(tool.getPlans(), PLANS),
                                 tool.getFreeTierPlanId(),
                                 tool.getHighlightPlanId(),
                                 JsonbArrays.readStringArray(tool.getIntegrations()),
                                 tool.getDemoVideoUrl(),
                                 screenshots,
                                 JsonbArrays.read(tool.getFaqs(), FAQS),
                                 Boolean.TRUE.equals(tool.getPublished()),
                                 tool.getApprovedAt(),
                                 tool.getUpdatedAt());
    }

    private JSONB toJsonb(Object value) {
        try {
            return JSONB.valueOf(objectMapper.writeValueAsString(value));
        } catch (JsonProcessingException e) {
            // Everything passed here was deserialised by Jackson moments ago, so this is a bug, not bad input.
            throw new IllegalStateException("Could not serialise " + value.getClass() + " to JSON", e);
        }
    }

    private static ResponseStatusException notListed(long vendorId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor " + vendorId + " has no listed tool");
    }
}
