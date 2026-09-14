package ro.prospero.aidir.service;

import org.jooq.DSLContext;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ro.prospero.aidir.data.ToolDTO;
import ro.prospero.aidir.event.ToolUploadEventPublisher;
import ro.prospero.aidir.jooq.generated.public_.tables.records.ToolSubmissionRecord;

import java.time.OffsetDateTime;
import java.util.List;

import static ro.prospero.aidir.jooq.generated.public_.Tables.TOOL_SUBMISSION;

@Service
public class ToolSubmissionService {
    private final DSLContext dslContext;
    private final ModelMapper beanMapper;
    private final DirectusService directusService;
    private final ToolUploadEventPublisher toolUploadEventPublisher;
    private static final Logger LOGGER = LoggerFactory.getLogger(ToolSubmissionService.class);

    public ToolSubmissionService(DSLContext dslContext,
                                 @Qualifier("beanMapper") ModelMapper beanMapper,
                                 DirectusService directusService,
                                 ToolUploadEventPublisher toolUploadEventPublisher) {
        this.dslContext = dslContext;
        this.beanMapper = beanMapper;
        this.directusService = directusService;
        this.toolUploadEventPublisher = toolUploadEventPublisher;
    }

    public List<ToolDTO> getQueue() {
        List<ToolSubmissionRecord> queue = dslContext.selectFrom(TOOL_SUBMISSION)
                                                     .where(TOOL_SUBMISSION.APPROVED.isNull())
                                                     .orderBy(TOOL_SUBMISSION.SUBMITTED_AT.asc())
                                                     .fetch();

        List<ToolDTO> mapped = queue.stream().map(r -> beanMapper.map(r, ToolDTO.class)).toList();

        return mapped;
    }

    public void approve(ToolDTO toolDTO) {
        Long id = toolDTO.getId();
        dslContext.update(TOOL_SUBMISSION)
                  .set(TOOL_SUBMISSION.APPROVED, true)
                  .set(TOOL_SUBMISSION.APPROVED_AT, OffsetDateTime.now())
                  .where(TOOL_SUBMISSION.ID.eq(id))
                  .execute();

        LOGGER.info("pre directus upload");
        directusService.uploadTool(toolDTO);
        toolUploadEventPublisher.publish(toolDTO);


        dslContext.update(TOOL_SUBMISSION)
                  .set(TOOL_SUBMISSION.UPLOADED_TO_CMS, true)
                  .where(TOOL_SUBMISSION.ID.eq(id))
                  .execute();
    }

    // todo: should support some rejection message probably
    public void reject(ToolDTO toolDTO) {
        Long id = toolDTO.getId();
        dslContext.update(TOOL_SUBMISSION)
                  .set(TOOL_SUBMISSION.APPROVED, false)
                  .where(TOOL_SUBMISSION.ID.eq(id))
                  .execute();
    }
}
