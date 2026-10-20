package ro.prospero.aidir.service;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import ro.prospero.aidir.data.ArticleQuoteDTO;
import ro.prospero.aidir.data.ArticleSubmissionPayload;
import ro.prospero.aidir.data.SubmittedArticleDTO;
import ro.prospero.aidir.jooq.generated.tables.records.SubmittedArticleRecord;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static ro.prospero.aidir.jooq.generated.Tables.SUBMITTED_ARTICLE;
import static ro.prospero.aidir.jooq.generated.Tables.SUBMITTED_ARTICLE_IMAGE;
import static ro.prospero.aidir.jooq.generated.Tables.SUBMITTED_ARTICLE_TOPIC;

/**
 * A vendor's own articles, written by them and sent to be published: submitting one, and reading back their
 * own. Each is priced here by {@link ArticlePricing} from what its body holds, not by what the client says it
 * costs. Every read is scoped by the account as well as the id, so another vendor's article answers as one
 * that does not exist.
 */
@Service
public class SubmittedArticleService {
    /** STANDARD_TEMPLATE.maxImages in src/components/article-submission/template.ts. */
    static final int MAX_IMAGES = 4;

    private final DSLContext dslContext;
    private final SubmittedArticleRows rows;
    private final ArticleOrderChecks checks;
    private final FileStorageService fileStorageService;

    public SubmittedArticleService(DSLContext dslContext, SubmittedArticleRows rows, ArticleOrderChecks checks,
                                   FileStorageService fileStorageService) {
        this.dslContext = dslContext;
        this.rows = rows;
        this.checks = checks;
        this.fileStorageService = fileStorageService;
    }

    public List<SubmittedArticleDTO> list(long accountId) {
        return rows.read(dslContext, SUBMITTED_ARTICLE.ACCOUNT_ID.eq(accountId), SUBMITTED_ARTICLE.CREATED_AT.desc())
                   .stream()
                   .map(SubmittedArticleRows.Stored::article)
                   .toList();
    }

    public SubmittedArticleDTO get(long accountId, long articleId) {
        return rows.readOne(dslContext,
                            SUBMITTED_ARTICLE.ID.eq(articleId).and(SUBMITTED_ARTICLE.ACCOUNT_ID.eq(accountId)))
                   .map(SubmittedArticleRows.Stored::article)
                   .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                                  "No submitted article with id " + articleId));
    }

    /**
     * The links billed are the ones written into the body.
     *
     * @param featuredImage required; null when the request carried none
     * @param images        the in-article images, in the order the body's image blocks number them
     */
    public SubmittedArticleDTO submit(long accountId, ArticleSubmissionPayload payload, MultipartFile featuredImage,
                                      List<MultipartFile> images) {
        List<String> violations = new ArrayList<>();
        checks.checkTopics(payload.topics(), violations);
        violations.addAll(ArticleBodyRules.checkBody(payload.body(), images.size()));
        if (featuredImage == null) {
            violations.add("featuredImage: Add the image to use as the article cover");
        } else {
            ArticleOrderChecks.checkImage("featuredImage", featuredImage, violations);
        }
        if (images.size() > MAX_IMAGES) {
            violations.add("additionalImages: Add at most " + MAX_IMAGES + " images to the article");
        }
        for (int i = 0; i < images.size(); i++) {
            ArticleOrderChecks.checkImage("additionalImages[" + i + "]", images.get(i), violations);
        }
        ArticleOrderChecks.throwIfAny("article", violations);

        ArticleSubmissionPayload.Options options = payload.options();
        int links = ArticleBodyRules.billedLinks(payload.body());
        ArticleQuoteDTO quote = ArticlePricing.quote(links, null, options.permanentLinks(), options.unlabelled(),
                                                     images.size());
        ArticleOrderChecks.requireQuoted(payload.quotedTotal(), quote);

        StagedImages staged = new StagedImages(fileStorageService, "articles");
        long articleId;
        try {
            String featuredPath = staged.store(featuredImage, "featured");
            List<String> imagePaths = new ArrayList<>();
            for (int i = 0; i < images.size(); i++) {
                imagePaths.add(staged.store(images.get(i), "image-" + i));
            }

            articleId = dslContext.transactionResult(cfg -> {
                DSLContext tx = DSL.using(cfg);
                SubmittedArticleRecord row = tx.newRecord(SUBMITTED_ARTICLE);
                row.setAccountId(accountId);
                row.setHeadline(payload.headline());
                row.setFeaturedImagePath(featuredPath);
                row.setBody(ArticleJson.writeBody(payload.body()));
                row.setSources(ArticleJson.write(payload.sources()));
                row.setLinks(links);
                row.setPermanentLinks(options.permanentLinks());
                row.setUnlabelled(options.unlabelled());
                row.setQuote(ArticleJson.write(quote));
                row.setTotalEur(quote.total());
                row.setConsentedAt(OffsetDateTime.now());
                row.insert();

                for (String topic : new LinkedHashSet<>(payload.topics())) {
                    tx.insertInto(SUBMITTED_ARTICLE_TOPIC)
                      .set(SUBMITTED_ARTICLE_TOPIC.SUBMITTED_ARTICLE_ID, row.getId())
                      .set(SUBMITTED_ARTICLE_TOPIC.TOPIC, topic)
                      .execute();
                }
                for (int i = 0; i < imagePaths.size(); i++) {
                    tx.insertInto(SUBMITTED_ARTICLE_IMAGE)
                      .set(SUBMITTED_ARTICLE_IMAGE.SUBMITTED_ARTICLE_ID, row.getId())
                      .set(SUBMITTED_ARTICLE_IMAGE.POSITION, i)
                      .set(SUBMITTED_ARTICLE_IMAGE.PATH, imagePaths.get(i))
                      .execute();
                }
                return row.getId();
            });
        } catch (RuntimeException exception) {
            staged.discard();
            throw exception;
        }
        return get(accountId, articleId);
    }
}
