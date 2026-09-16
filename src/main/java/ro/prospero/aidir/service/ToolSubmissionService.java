package ro.prospero.aidir.service;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ro.prospero.aidir.data.ToolDTO;
import ro.prospero.aidir.data.ToolSubmissionDTO;
import ro.prospero.aidir.event.ToolUploadEventPublisher;
import ro.prospero.aidir.jooq.generated.public_.tables.records.ToolSubmissionRecord;

import java.time.OffsetDateTime;
import java.util.List;

import static ro.prospero.aidir.jooq.generated.public_.Tables.TOOL;
import static ro.prospero.aidir.jooq.generated.public_.Tables.TOOL_IMAGE_METADATA;
import static ro.prospero.aidir.jooq.generated.public_.Tables.TOOL_SUBMISSION;
import static ro.prospero.aidir.jooq.generated.public_.Tables.TOOL_SUBMISSION_IMAGE_METADATA;

@Service
public class ToolSubmissionService {
    private final DSLContext dslContext;
    private final ModelMapper beanMapper;
    private final ToolService toolService;
    private final ToolUploadEventPublisher toolUploadEventPublisher;
    private static final Logger LOGGER = LoggerFactory.getLogger(ToolSubmissionService.class);

    public ToolSubmissionService(DSLContext dslContext,
                                 @Qualifier("beanMapper") ModelMapper beanMapper,
                                 ToolService toolService,
                                 ToolUploadEventPublisher toolUploadEventPublisher) {
        this.dslContext = dslContext;
        this.beanMapper = beanMapper;
        this.toolService = toolService;
        this.toolUploadEventPublisher = toolUploadEventPublisher;
    }

    public List<ToolSubmissionDTO> getQueue() {
        List<ToolSubmissionRecord> queue = dslContext.selectFrom(TOOL_SUBMISSION)
                                                     .where(TOOL_SUBMISSION.APPROVED.isNull())
                                                     .orderBy(TOOL_SUBMISSION.SUBMITTED_AT.asc())
                                                     .fetch();

        return queue.stream().map(r -> beanMapper.map(r, ToolSubmissionDTO.class)).toList();
    }

    /**
     * Lists a submission: the row is marked approved and copied into {@code tool}, which is the catalogue
     * the details page and the search index read.
     *
     * <p>Only the id of the argument is used. The rest is re-read from the database, partly because the
     * caller echoes back a queue row it could have edited in the browser, and partly because the copy
     * carries columns - features, plans, integrations, the images - that the queue payload never had.
     *
     * <p>The copy is one jOOQ transaction rather than {@code @Transactional} for the reason given on
     * {@link VendorOnboardingService}: JooqConfig builds its DSLContext over the raw DataSource, so
     * Spring's transaction manager would not enrol these statements.
     */
    public void approve(ToolSubmissionDTO submissionDTO) {
        if (submissionDTO.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot approve a submission with no id");
        }
        long submissionId = submissionDTO.getId();

        Long toolId = dslContext.transactionResult(cfg -> {
            DSLContext tx = DSL.using(cfg);

            // Locked for the duration: two admins double-clicking Approve on the same row would otherwise
            // both find nothing listed and both insert.
            ToolSubmissionRecord submission = tx.selectFrom(TOOL_SUBMISSION)
                                                .where(TOOL_SUBMISSION.ID.eq(submissionId))
                                                .forUpdate()
                                                .fetchOne();
            if (submission == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                  "No submission with id " + submissionId);
            }

            tx.update(TOOL_SUBMISSION)
              .set(TOOL_SUBMISSION.APPROVED, true)
              .set(TOOL_SUBMISSION.APPROVED_AT, OffsetDateTime.now())
              .where(TOOL_SUBMISSION.ID.eq(submissionId))
              .execute();

            // null means "already in the catalogue", which is not an error: approving twice should be the
            // same as approving once, not a second listing and a duplicate search hit.
            if (isListed(tx, submissionId)) {
                return null;
            }

            long id = insertTool(tx, submission);
            copyImageMetadata(tx, submissionId, id);

            tx.update(TOOL_SUBMISSION)
              .set(TOOL_SUBMISSION.PUBLISHED, true)
              .where(TOOL_SUBMISSION.ID.eq(submissionId))
              .execute();

            return id;
        });

        if (toolId == null) {
            LOGGER.info("Submission {} is already listed; left the catalogue and the index as they were",
                        submissionId);
            return;
        }

        // Published after the commit, not inside it: the listener indexes asynchronously, so a tool handed
        // over mid-transaction could be indexed and then rolled out from under the index.
        ToolDTO listed = toolService.find(toolId)
                                    .orElseThrow(() -> new IllegalStateException(
                                            "Tool " + toolId + " is missing right after being listed"));
        toolUploadEventPublisher.publish(listed);
    }

    // todo: should support some rejection message probably
    public void reject(ToolSubmissionDTO submissionDTO) {
        Long id = submissionDTO.getId();
        dslContext.update(TOOL_SUBMISSION)
                  .set(TOOL_SUBMISSION.APPROVED, false)
                  .where(TOOL_SUBMISSION.ID.eq(id))
                  .execute();
    }

    private boolean isListed(DSLContext tx, long submissionId) {
        return tx.fetchExists(tx.selectFrom(TOOL).where(TOOL.TOOL_SUBMISSION_ID.eq(submissionId)));
    }

    /**
     * {@code approved_at} and {@code published} are left to their column defaults - now(), and listed.
     */
    private long insertTool(DSLContext tx, ToolSubmissionRecord submission) {
        Long toolId = tx.insertInto(TOOL)
                        .set(TOOL.TOOL_SUBMISSION_ID, submission.getId())
                        .set(TOOL.ACCOUNT_ID, submission.getAccountId())
                        .set(TOOL.NAME, submission.getName())
                        .set(TOOL.URL, submission.getUrl())
                        .set(TOOL.PRICING, submission.getPricing())
                        .set(TOOL.SHORT_DESCRIPTION, submission.getShortDescription())
                        .set(TOOL.LONG_DESCRIPTION, submission.getLongDescription())
                        .set(TOOL.TAGS, submission.getTags())
                        .set(TOOL.CATEGORIES, submission.getCategories())
                        .set(TOOL.FEATURES, submission.getFeatures())
                        .set(TOOL.PLANS, submission.getPlans())
                        .set(TOOL.FREE_TIER_PLAN_ID, submission.getFreeTierPlanId())
                        .set(TOOL.HIGHLIGHT_PLAN_ID, submission.getHighlightPlanId())
                        .set(TOOL.INTEGRATIONS, submission.getIntegrations())
                        .set(TOOL.DEMO_VIDEO_URL, submission.getDemoVideoUrl())
                        .set(TOOL.SUBMITTED_BY, submission.getSubmittedBy())
                        .set(TOOL.SUBMITTED_AT, submission.getSubmittedAt())
                        .returningResult(TOOL.ID)
                        .fetchOne(TOOL.ID);
        if (toolId == null) {
            throw new IllegalStateException("Listing tool submission " + submission.getId()
                                            + " returned no id");
        }
        return toolId;
    }

    /**
     * The paths are copied as they are, so both rows name the same file under the uploads root. Nothing is
     * moved or re-uploaded: a listing being pulled must not take the submission's evidence with it.
     */
    private void copyImageMetadata(DSLContext tx, long submissionId, long toolId) {
        tx.insertInto(TOOL_IMAGE_METADATA,
                      TOOL_IMAGE_METADATA.TOOL_ID,
                      TOOL_IMAGE_METADATA.IMAGE_PATH,
                      TOOL_IMAGE_METADATA.KIND,
                      TOOL_IMAGE_METADATA.DISPLAY_ORDER)
          .select(tx.select(DSL.val(toolId),
                            TOOL_SUBMISSION_IMAGE_METADATA.IMAGE_PATH,
                            TOOL_SUBMISSION_IMAGE_METADATA.KIND,
                            TOOL_SUBMISSION_IMAGE_METADATA.DISPLAY_ORDER)
                    .from(TOOL_SUBMISSION_IMAGE_METADATA)
                    .where(TOOL_SUBMISSION_IMAGE_METADATA.TOOL_SUBMISSION_ID.eq(submissionId))
                    .orderBy(TOOL_SUBMISSION_IMAGE_METADATA.DISPLAY_ORDER.asc()))
          .execute();
    }
}
