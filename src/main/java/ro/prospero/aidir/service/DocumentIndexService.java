package ro.prospero.aidir.service;

import lombok.AllArgsConstructor;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ro.prospero.aidir.data.ToolDTO;
import ro.prospero.aidir.search.LuceneDocumentMapper;
import ro.prospero.aidir.search.LuceneIndexManager;
import ro.prospero.aidir.search.LuceneToolFields;

import java.io.IOException;
import java.util.List;

@Service
@AllArgsConstructor
public class DocumentIndexService {
    private final LuceneDocumentMapper mapper;
    private final LuceneIndexManager manager;
    private final DirectusService directusService;
    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentIndexService.class);

    public void rebuildAll() {
        try {
            IndexWriter indexWriter = manager.getIndexWriter();
            List<ToolDTO> tools = directusService.getAllTools();

            List<Document> luceneDocuments = tools.stream().map(mapper::toLuceneDocument).toList();
            indexWriter.addDocuments(luceneDocuments);
            indexWriter.commit();
            manager.getSearcherManager().maybeRefreshBlocking();

        } catch (IOException e) {
            LOGGER.error("Error when rebuilding index: ", e);
            throw new RuntimeException(e);
        }
    }

    // for all these operations (create, update, delete)
    // the changes will eventually (on the order of milliseconds) be available in the index, (see reopenThread LuceneIndexManager)
    // but it is worth noting that index changes are only eventually consistent

    public void create(ToolDTO toolDTO) {
        try {
            LOGGER.info("Creating search document for tool: {}", toolDTO);
            Document luceneDocument = mapper.toLuceneDocument(toolDTO);
            manager.getIndexWriter().addDocument(luceneDocument);

        } catch (IOException e) {
            LOGGER.error("Error when creating document:", e);
            throw new IllegalStateException("Failed to index document " + toolDTO.getId(), e);
        }
    }

    public void update(ToolDTO toolDTO) {
        try {
            manager.getIndexWriter().updateDocument(new Term(LuceneToolFields.DOC_KEY, mapper.docKey(toolDTO)),
                                                    mapper.toLuceneDocument(toolDTO));
        } catch (IOException e) {
            LOGGER.error("Error when updating document:", e);
            throw new IllegalStateException("Failed to update document " + toolDTO.getId(), e);
        }
    }

    public void deleteByDocId(ToolDTO toolDTO) {
        try {
            manager.getIndexWriter().deleteDocuments(new Term(LuceneToolFields.DOC_KEY, mapper.docKey(toolDTO)));
        } catch (IOException e) {
            LOGGER.error("Error when deleting document:", e);
            throw new IllegalStateException("Failed to delete document " + toolDTO.getId(), e);
        }
    }


}
