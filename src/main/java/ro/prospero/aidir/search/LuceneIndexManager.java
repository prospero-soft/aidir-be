package ro.prospero.aidir.search;

import jakarta.annotation.PreDestroy;
import lombok.Getter;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.ControlledRealTimeReopenThread;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class LuceneIndexManager {

    private final Directory directory;
    @Getter
    private final Analyzer analyzer;
    @Getter
    private final IndexWriter indexWriter;
    @Getter
    private final SearcherManager searcherManager;
    private final ControlledRealTimeReopenThread<IndexSearcher> reopenThread;

    public LuceneIndexManager() throws IOException {
        this.directory = new ByteBuffersDirectory();
        this.analyzer = new StandardAnalyzer();
        IndexWriterConfig indexWriterConfig = new IndexWriterConfig(analyzer);
        indexWriterConfig.setCommitOnClose(true);

        this.indexWriter = new IndexWriter(directory, indexWriterConfig);
        this.searcherManager = new SearcherManager(indexWriter, new SearcherFactory());
        this.reopenThread = new ControlledRealTimeReopenThread<>(indexWriter, searcherManager, 5.0, 0.1);
        this.reopenThread.setName("lucene-nrt-reopen-thread");
        this.reopenThread.setDaemon(true);
        this.reopenThread.start();
    }

    @PreDestroy
    public void close() {
        try {
            reopenThread.close();
        } catch (Exception ignored) {
        }
        try {
            searcherManager.close();
        } catch (Exception ignored) {
        }
        try {
            indexWriter.close();
        } catch (Exception ignored) {
        }
        try {
            analyzer.close();
        } catch (Exception ignored) {
        }
        try {
            directory.close();
        } catch (Exception ignored) {
        }
    }


}
