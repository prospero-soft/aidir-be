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

        Long safeId = required(toolDTO.id());
        String docKey = docKey(toolDTO);
        String name = safe(toolDTO.name());
        String shortDescription = safe(toolDTO.shortDescription());

        document.add(new StringField(LuceneToolFields.DOC_KEY, docKey, Field.Store.YES));
        document.add(new LongField(LuceneToolFields.ID, safeId, Field.Store.YES));
        document.add(new TextField(LuceneToolFields.NAME, name, Field.Store.YES));
        document.add(new TextField(LuceneToolFields.NAME_AUTOCOMPLETE, name, Field.Store.NO));
        // One field per category, not one joined value: a multi-valued StringField is what lets a term
        // query on any single category match a tool listed under several.
        for (String category : normalizeTerms(toolDTO.categories())) {
            document.add(new StringField(LuceneToolFields.CATEGORY_EXACT, category, Field.Store.YES));
        }

        document.add(new TextField(LuceneToolFields.SHORT_DESCRIPTION, shortDescription, Field.Store.YES));
        document.add(new TextField(LuceneToolFields.SHORT_DESCRIPTION_AUTOCOMPLETE, shortDescription, Field.Store.NO));
        document.add(new TextField(LuceneToolFields.LONG_DESCRIPTION, safe(toolDTO.longDescription()), Field.Store.NO));

        for (String tag : normalizeTerms(toolDTO.tags())) {
            document.add(new StringField(LuceneToolFields.TAGS_EXACT, tag, Field.Store.YES));
        }

        return document;
    }

    /**
     * Tags and categories are both indexed as exact terms, so both go through here: trimmed, lower-cased
     * and de-duplicated, because a term query only ever matches what was written verbatim.
     */
    public List<String> normalizeTerms(List<String> terms) {
        if (terms == null || terms.isEmpty()) {
            return List.of();
        }

        Set<String> normalized = new LinkedHashSet<>();
        for (String term : terms) {
            if (term == null) {
                continue;
            }
            String value = normalizeTerm(term);
            if (!value.isEmpty()) {
                normalized.add(value);
            }
        }
        return new ArrayList<>(normalized);
    }

    public String normalizeTerm(String term) {
        if (term == null) {
            return "";
        }
        return term.trim().toLowerCase(Locale.ROOT);
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
        Long safeId = required(toolDTO.id());
        String docKey = LuceneToolFields.DOC_TYPE + ":" + safeId;
        return docKey;
    }
}
