package ro.prospero.aidir.service;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import ro.prospero.aidir.data.ArticleBriefDTO;
import ro.prospero.aidir.data.ArticleBriefPayload;
import ro.prospero.aidir.data.ArticleQuoteDTO;
import ro.prospero.aidir.jooq.generated.tables.records.ArticleBriefRecord;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static ro.prospero.aidir.jooq.generated.Tables.ARTICLE_BRIEF;
import static ro.prospero.aidir.jooq.generated.Tables.ARTICLE_BRIEF_TOPIC;

/**
 * A vendor's briefs for articles our editors write: placing one, and reading back their own. Each is priced
 * here by {@link ArticlePricing} from the writing tier and links the vendor chose. Every read is scoped by the
 * account as well as the id, so another vendor's brief answers as one that does not exist.
 */
@Service
public class ArticleBriefService {
    private final DSLContext dslContext;
    private final ArticleBriefRows rows;
    private final ArticleOrderChecks checks;
    private final FileStorageService fileStorageService;

    public ArticleBriefService(DSLContext dslContext, ArticleBriefRows rows, ArticleOrderChecks checks,
                               FileStorageService fileStorageService) {
        this.dslContext = dslContext;
        this.rows = rows;
        this.checks = checks;
        this.fileStorageService = fileStorageService;
    }

    public List<ArticleBriefDTO> list(long accountId) {
        return rows.read(dslContext, ARTICLE_BRIEF.ACCOUNT_ID.eq(accountId), ARTICLE_BRIEF.CREATED_AT.desc())
                   .stream()
                   .map(ArticleBriefRows.Stored::brief)
                   .toList();
    }

    public ArticleBriefDTO get(long accountId, long briefId) {
        return rows.readOne(dslContext, ARTICLE_BRIEF.ID.eq(briefId).and(ARTICLE_BRIEF.ACCOUNT_ID.eq(accountId)))
                   .map(ArticleBriefRows.Stored::brief)
                   .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                                  "No article brief with id " + briefId));
    }

    /** @param featuredImage optional; null leaves the cover to our editors */
    public ArticleBriefDTO place(long accountId, ArticleBriefPayload payload, MultipartFile featuredImage) {
        List<String> violations = new ArrayList<>();
        checks.checkTopics(payload.topics(), violations);
        if (featuredImage != null) {
            ArticleOrderChecks.checkImage("featuredImage", featuredImage, violations);
        }
        ArticleOrderChecks.throwIfAny("brief", violations);

        ArticleBriefPayload.Options options = payload.options();
        ArticleQuoteDTO quote = ArticlePricing.quote(options.links(), options.writing(), options.permanentLinks(),
                                                     options.unlabelled(), 0);
        ArticleOrderChecks.requireQuoted(payload.quotedTotal(), quote);

        String audience = payload.audience() == null || payload.audience().isBlank() ? null : payload.audience();
        StagedImages staged = new StagedImages(fileStorageService, "briefs");
        long briefId;
        try {
            String featuredPath = featuredImage == null ? null : staged.store(featuredImage, "featured");

            briefId = dslContext.transactionResult(cfg -> {
                DSLContext tx = DSL.using(cfg);
                ArticleBriefRecord row = tx.newRecord(ARTICLE_BRIEF);
                row.setAccountId(accountId);
                row.setHeadline(payload.headline());
                row.setBrief(payload.brief());
                row.setAudience(audience);
                row.setKeyMessages(ArticleJson.write(payload.keyMessages()));
                row.setPreferredDate(payload.preferredDate());
                row.setWritingTier(options.writing().wire());
                row.setFeaturedImagePath(featuredPath);
                row.setSources(ArticleJson.write(payload.sources()));
                row.setLinks(options.links());
                row.setPermanentLinks(options.permanentLinks());
                row.setUnlabelled(options.unlabelled());
                row.setQuote(ArticleJson.write(quote));
                row.setTotalEur(quote.total());
                row.setConsentedAt(OffsetDateTime.now());
                row.insert();

                for (String topic : new LinkedHashSet<>(payload.topics())) {
                    tx.insertInto(ARTICLE_BRIEF_TOPIC)
                      .set(ARTICLE_BRIEF_TOPIC.ARTICLE_BRIEF_ID, row.getId())
                      .set(ARTICLE_BRIEF_TOPIC.TOPIC, topic)
                      .execute();
                }
                return row.getId();
            });
        } catch (RuntimeException exception) {
            staged.discard();
            throw exception;
        }
        return get(accountId, briefId);
    }
}
