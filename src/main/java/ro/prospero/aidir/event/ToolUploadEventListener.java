package ro.prospero.aidir.event;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import ro.prospero.aidir.service.DocumentIndexService;

import static ro.prospero.aidir.config.AsyncConfig.SEARCH_EXECUTOR;

@Component
@AllArgsConstructor
public class ToolUploadEventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(ToolUploadEventListener.class);

    private final DocumentIndexService documentIndexService;

    @Async(SEARCH_EXECUTOR)
    @EventListener
    public void onToolUpload(ToolUploadEvent toolUploadEvent) {
        LOGGER.info("Indexing tool: {}", toolUploadEvent.tool());
        documentIndexService.create(toolUploadEvent.tool());
    }
}
