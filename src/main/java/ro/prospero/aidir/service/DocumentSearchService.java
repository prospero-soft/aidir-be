package ro.prospero.aidir.service;

import lombok.AllArgsConstructor;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.xml.builders.BooleanQueryBuilder;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ro.prospero.aidir.data.SearchRequest;
import ro.prospero.aidir.data.ToolSearchHit;
import ro.prospero.aidir.search.LuceneDocumentMapper;
import ro.prospero.aidir.search.LuceneIndexManager;
import ro.prospero.aidir.search.LuceneToolFields;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@AllArgsConstructor
public class DocumentSearchService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentSearchService.class);

    private static final String[] SEARCH_FIELDS = {
            LuceneToolFields.NAME,
            LuceneToolFields.SHORT_DESCRIPTION,
            LuceneToolFields.LONG_DESCRIPTION
    };

    private static final Map<String, Float> BOOSTS = Map.of(
            LuceneToolFields.NAME, 4.0f,
            LuceneToolFields.SHORT_DESCRIPTION, 2.0f,
            LuceneToolFields.LONG_DESCRIPTION, 1.0f
    );

    private final LuceneIndexManager indexManager;
    private final LuceneDocumentMapper mapper;

    public List<ToolSearchHit> search(String queryText, int limit) {
        SearcherManager searcherManager = indexManager.getSearcherManager();
        IndexSearcher searcher = null;

        try {
            searcher = searcherManager.acquire();

            Query textQuery = buildTextQuery(queryText);

            TopDocs topDocs = searcher.search(textQuery, Math.max(1, limit));

            List<ToolSearchHit> results = new ArrayList<>(topDocs.scoreDocs.length);
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.storedFields().document(scoreDoc.doc);

                results.add(new ToolSearchHit(
                        Long.valueOf(doc.get(LuceneToolFields.ID)),
                        doc.get(LuceneToolFields.NAME),
                        doc.get(LuceneToolFields.SHORT_DESCRIPTION),
                        mapper.readStoredTags(doc)
                ));
            }

            return results;
        } catch (Exception e) {
            throw new IllegalStateException("Search failed for query: " + queryText, e);
        } finally {
            if (searcher != null) {
                try {
                    searcherManager.release(searcher);
                } catch (Exception ignored) {
                }
            }
        }
    }

    public List<ToolSearchHit> search(String queryText, String docType, List<String> exactTags, int limit) {
        SearcherManager searcherManager = indexManager.getSearcherManager();
        IndexSearcher searcher = null;

        try {
            searcher = searcherManager.acquire();

            Query textQuery = buildTextQuery(queryText);

            BooleanQuery.Builder builder = new BooleanQuery.Builder()
                    .add(textQuery, BooleanClause.Occur.MUST);

            if (docType != null && !docType.isBlank()) {
                builder.add(
                        new TermQuery(new Term(LuceneToolFields.DOC_TYPE, docType)),
                        BooleanClause.Occur.FILTER
                );
            }

            if (exactTags != null) {
                for (String tag : exactTags) {
                    if (tag == null || tag.isBlank()) {
                        continue;
                    }
                    builder.add(
                            new TermQuery(new Term(LuceneToolFields.TAGS_EXACT, tag)),
                            BooleanClause.Occur.FILTER
                    );
                }
            }

            Query finalQuery = builder.build();
            TopDocs topDocs = searcher.search(finalQuery, Math.max(1, limit));

            List<ToolSearchHit> results = new ArrayList<>(topDocs.scoreDocs.length);
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.storedFields().document(scoreDoc.doc);

                results.add(new ToolSearchHit(
                        mapper.docIdToId(doc.get(LuceneToolFields.ID)),
                        doc.get(LuceneToolFields.NAME),
                        doc.get(LuceneToolFields.SHORT_DESCRIPTION),
                        mapper.readStoredTags(doc)
                ));
            }

            return results;
        } catch (Exception e) {
            throw new IllegalStateException("Search failed for query: " + queryText, e);
        } finally {
            if (searcher != null) {
                try {
                    searcherManager.release(searcher);
                } catch (Exception ignored) {
                }
            }
        }
    }

    public List<ToolSearchHit> search(SearchRequest request) {
        try {
            Query query = buildQuery(request);
            return searchAfter(query, request.limit(), null);
        } catch (ParseException ex) {
            LOGGER.error("Error when parsing search request: {}", request, ex);
            throw new IllegalArgumentException("Couldn't parse search request");
        }
    }


    // todo: add category filter support (on the frontend this should also allow for writing filters
    //  in the search input, e.g. #cat:coding etc.)


    public List<ToolSearchHit> searchAfter(Query query, int reqLimit, ScoreDoc after) {
        SearcherManager searcherManager = indexManager.getSearcherManager();
        IndexSearcher searcher = null;

        try {
            searcher = searcherManager.acquire();

            int limit = Math.max(1, Math.min(reqLimit, 100));
            TopDocs topDocs = after == null
                    ? searcher.search(query, limit)
                    : searcher.searchAfter(after, query, limit);

            List<ToolSearchHit> results = new ArrayList<>(topDocs.scoreDocs.length);
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.storedFields().document(scoreDoc.doc);
                results.add(new ToolSearchHit(
                        mapper.docIdToId(doc.get(LuceneToolFields.ID)),
                        doc.get(LuceneToolFields.NAME),
                        doc.get(LuceneToolFields.SHORT_DESCRIPTION),
                        mapper.readStoredTags(doc)
                ));
            }

            return results;
        } catch (Exception e) {
            throw new IllegalStateException("Search failed", e);
        } finally {
            if (searcher != null) {
                try {
                    searcherManager.release(searcher);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private Query buildQuery(SearchRequest request) throws ParseException {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();

        builder.add(buildTextQuery(request.queryText()), BooleanClause.Occur.MUST);

        if (request.docType() != null && !request.docType().isBlank()) {
            builder.add(
                    new TermQuery(new Term(LuceneToolFields.DOC_TYPE, request.docType().trim())),
                    BooleanClause.Occur.FILTER
            );
        }

        addTagPrefixFilters(builder, request.queryText());

        return builder.build();
    }

    private Query buildTextQuery(String queryText) throws ParseException {
        String normalized = queryText == null ? "" : queryText.trim();
        if (normalized.isBlank()) {
            return MatchAllDocsQuery.INSTANCE;
        }

        MultiFieldQueryParser parser = new MultiFieldQueryParser(
                SEARCH_FIELDS,
                indexManager.getAnalyzer(),
                BOOSTS
        );
        parser.setDefaultOperator(QueryParser.Operator.OR);

        return parser.parse(QueryParser.escape(normalized));
    }

    private Query buildCategoryQuery(List<String> categories) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        categories.stream()
                  .filter(Objects::nonNull)
                  .forEach(category -> {
            builder.add(new TermQuery(new Term(LuceneToolFields.CATEGORY_EXACT, category)),
                        BooleanClause.Occur.SHOULD);
        });
        return builder.build();
    }

    private void addExactTagFilters(BooleanQuery.Builder builder, List<String> exactTags) {
        if (exactTags == null) {
            return;
        }

        for (String rawTag : exactTags) {
            String tag = mapper.normalizeTag(rawTag);
            if (tag.isEmpty()) {
                continue;
            }

            builder.add(
                    new TermQuery(new Term(LuceneToolFields.TAGS_EXACT, tag)),
                    BooleanClause.Occur.FILTER
            );
        }
    }

    private void addTagPrefixFilters(BooleanQuery.Builder builder, String tagPrefix) {
        if (tagPrefix == null) {
            return;
        }

            String prefix = mapper.normalizeTag(tagPrefix);
            if (prefix.isEmpty()) {
                return;
            }

            builder.add(
                    new PrefixQuery(new Term(LuceneToolFields.TAGS_EXACT, prefix)),
                    BooleanClause.Occur.FILTER
            );
    }



}
