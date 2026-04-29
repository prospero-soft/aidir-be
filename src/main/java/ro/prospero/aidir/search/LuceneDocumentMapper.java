package ro.prospero.aidir.search;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ro.prospero.aidir.data.ToolDTO;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class LuceneDocumentMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(LuceneDocumentMapper.class);

    public Document toLuceneDocument(ToolDTO toolDTO) {
        Document document = new Document();

        Long safeId = required(toolDTO.getId());
        String docKey = docKey(toolDTO);

        document.add(new StringField(LuceneToolFields.DOC_KEY, docKey, Field.Store.YES));
        document.add(new LongField(LuceneToolFields.ID, safeId,Field.Store.YES));
        document.add(new TextField(LuceneToolFields.NAME, safe(toolDTO.getName()), Field.Store.YES));
        document.add(new StringField(LuceneToolFields.CATEGORY_EXACT, safe(toolDTO.getCategory()), Field.Store.YES));
        document.add(new TextField(LuceneToolFields.SHORT_DESCRIPTION, safe(toolDTO.getShortDescription()), Field.Store.YES));
        document.add(new TextField(LuceneToolFields.LONG_DESCRIPTION, safe(toolDTO.getLongDescription()), Field.Store.NO));

        for (String tag : normalizeTags(toolDTO.getTags())) {
            document.add(new StringField(LuceneToolFields.TAGS_EXACT, tag, Field.Store.YES));
        }

        return document;
    }

    public List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }

        Set<String> normalized = new LinkedHashSet<>();
        for (String tag : tags) {
            if (tag == null) {
                continue;
            }
            String value = tag.trim().toLowerCase(Locale.ROOT);
            if (!value.isEmpty()) {
                normalized.add(value);
            }
        }
        return new ArrayList<>(normalized);
    }

    public String normalizeTag(String tag) {
        if (tag == null) {
            return "";
        }
        return tag.trim().toLowerCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public List<String> readStoredTags(Document doc) {
        return List.of(doc.getValues(LuceneToolFields.TAGS_EXACT));
    }

    public Long docIdToId(String docId) {
        String id = docId.split(":")[1];
        return Long.valueOf(id);
    }

    private Long required(Long value) {
        if (value == null) {
            throw new IllegalArgumentException("Document id must not be null");
        }
        return value;
    }

    public String docKey(ToolDTO toolDTO) {
        Long safeId = required(toolDTO.getId());
        String docKey = LuceneToolFields.DOC_TYPE + ":" + safeId;
        return docKey;
    }
}
