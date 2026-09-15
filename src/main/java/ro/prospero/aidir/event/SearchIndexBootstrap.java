package ro.prospero.aidir.event;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ro.prospero.aidir.service.DocumentIndexService;

/**
 * Fills the search index on startup. It lives in a ByteBuffersDirectory and is lost with the JVM, so
 * without this a restart serves an empty search until someone remembers to call /api/search/reindex.
 * <p>
 * Run inline rather than on the search executor: the build is a single query and a few thousand documents
 * at most, and doing it before anything else can index keeps it from racing the deleteAll() in
 * {@link DocumentIndexService#rebuildAll()} against a tool approved seconds after startup.
 */
@Component
@AllArgsConstructor
public class SearchIndexBootstrap {
    private static final Logger LOGGER = LoggerFactory.getLogger(SearchIndexBootstrap.class);

    private final DocumentIndexService documentIndexService;

    @EventListener(ApplicationReadyEvent.class)
    public void buildIndex() {
        try {
            documentIndexService.rebuildAll();
            LOGGER.info("Search index built at startup");
        } catch (RuntimeException exception) {
            // An index that would not build is not a reason to refuse to start: everything that is not
            // search still works, and /api/search/reindex can retry once the cause is dealt with.
            LOGGER.error("Could not build the search index at startup; search is empty until a reindex",
                         exception);
        }
    }
}
