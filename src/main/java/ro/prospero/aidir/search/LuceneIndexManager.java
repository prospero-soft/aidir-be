package ro.prospero.aidir.search;

import jakarta.annotation.PreDestroy;
import lombok.Getter;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.ngram.EdgeNGramTokenFilter;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
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
import java.util.HashMap;
import java.util.Map;

@Component
public class LuceneIndexManager {

    private final Directory directory;
    private final Analyzer indexAnalyzer;
    @Getter
    private final Analyzer searchAnalyzer;
    @Getter
    private final IndexWriter indexWriter;
    @Getter
    private final SearcherManager searcherManager;
    private final ControlledRealTimeReopenThread<IndexSearcher> reopenThread;

    public LuceneIndexManager() throws IOException {
        this.directory = new ByteBuffersDirectory();
        this.searchAnalyzer = searchAnalyzer();

        this.indexAnalyzer = indexAnalyzer();
        IndexWriterConfig indexWriterConfig = new IndexWriterConfig(indexAnalyzer);
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
            indexAnalyzer.close();
        } catch (Exception ignored) {
        }
        try {
            searchAnalyzer.close();
        } catch (Exception ignored) {
        }
        try {
            directory.close();
        } catch (Exception ignored) {
        }
    }

    private static Analyzer indexAnalyzer() {
        Analyzer standard = new StandardAnalyzer();
        Analyzer autocomplete = new AutocompleteIndexAnalyzer();

        Map<String, Analyzer> perField = new HashMap<>();

        perField.put(LuceneToolFields.NAME_AUTOCOMPLETE, autocomplete);

        return new PerFieldAnalyzerWrapper(standard, perField);
    }

    private static Analyzer searchAnalyzer() {
        Analyzer standard = new StandardAnalyzer();
        Analyzer autocomplete = new AutocompleteSearchAnalyzer();

        Map<String, Analyzer> perField = new HashMap<>();

        perField.put(LuceneToolFields.NAME_AUTOCOMPLETE, autocomplete);
        perField.put(LuceneToolFields.SHORT_DESCRIPTION_AUTOCOMPLETE, autocomplete);

        return new PerFieldAnalyzerWrapper(standard, perField);
    }

    private static final class AutocompleteIndexAnalyzer extends Analyzer {

        @Override
        protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer tokenizer = new StandardTokenizer();
            TokenFilter lowerCase = new LowerCaseFilter(tokenizer);
            TokenFilter edgeNGram = new EdgeNGramTokenFilter(lowerCase, 2, 30, true);

            return new TokenStreamComponents(tokenizer, edgeNGram);
        }
    }

    private static final class AutocompleteSearchAnalyzer extends Analyzer {
        @Override
        protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer tokenizer = new StandardTokenizer();
            TokenFilter lowerCase = new LowerCaseFilter(tokenizer);

            return new TokenStreamComponents(tokenizer, lowerCase);
        }
    }


}
