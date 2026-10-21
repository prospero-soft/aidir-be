package ro.prospero.aidir.service;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import ro.prospero.aidir.data.ArticleBlock;
import ro.prospero.aidir.data.ArticleQuoteDTO;
import ro.prospero.aidir.data.ArticleRevisionPayload;
import ro.prospero.aidir.data.ArticleSubmissionPayload;
import ro.prospero.aidir.data.SubmittedArticleDTO;
import ro.prospero.aidir.data.SubmittedArticleStatus;
import ro.prospero.aidir.jooq.generated.tables.records.SubmittedArticleRecord;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static ro.prospero.aidir.jooq.generated.Tables.SUBMITTED_ARTICLE;
import static ro.prospero.aidir.jooq.generated.Tables.SUBMITTED_ARTICLE_IMAGE;
import static ro.prospero.aidir.jooq.generated.Tables.SUBMITTED_ARTICLE_REVIEW_ROUND;
import static ro.prospero.aidir.jooq.generated.Tables.SUBMITTED_ARTICLE_TOPIC;

/**
 * A vendor's own articles, written by them and sent to be published: submitting one, revising one that review
 * sent back, and reading back their own. Each is priced here by {@link ArticlePricing} from what its body
 * holds, not by what the client says it costs, and only once: a revision is held to the links and images paid
 * for. Every read is scoped by the account as well as the id, so another vendor's article answers as one that
 * does not exist.
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
        List<String> violations = checkContent(payload.topics(), payload.body(), images.size());
        if (featuredImage == null) {
            violations.add("featuredImage: Add the image to use as the article cover");
        } else {
            ArticleOrderChecks.checkImage("featuredImage", featuredImage, violations);
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
                row.setImages(images.size());
                row.setPermanentLinks(options.permanentLinks());
                row.setUnlabelled(options.unlabelled());
                row.setQuote(ArticleJson.write(quote));
                row.setTotalEur(quote.total());
                row.setConsentedAt(OffsetDateTime.now());
                row.insert();

                writeTopicsAndImages(tx, row.getId(), payload.topics(), imagePaths);
                return row.getId();
            });
        } catch (RuntimeException exception) {
            staged.discard();
            throw exception;
        }
        return get(accountId, articleId);
    }

    /**
     * Replaces the content of an article that review sent back, and returns it to review. Its options, quote
     * and total stay as they were paid; its links and images may be fewer than paid for, never more. Stored
     * images the revision no longer keeps, and a replaced cover, are deleted once it is saved.
     *
     * @param featuredImage the new cover; null keeps the stored one
     * @param newImages     the in-article images the payload's {@code keptImages} marks as new, in body order
     */
    public SubmittedArticleDTO resubmit(long accountId, long articleId, ArticleRevisionPayload payload,
                                        MultipartFile featuredImage, List<MultipartFile> newImages) {
        List<Integer> keptImages = payload.keptImages();
        List<String> violations = checkContent(payload.topics(), payload.body(), keptImages.size());
        if (featuredImage != null) {
            ArticleOrderChecks.checkImage("featuredImage", featuredImage, violations);
        }
        violations.addAll(keptImageViolations(keptImages, newImages.size()));
        for (int i = 0; i < newImages.size(); i++) {
            ArticleOrderChecks.checkImage("additionalImages[" + i + "]", newImages.get(i), violations);
        }
        ArticleOrderChecks.throwIfAny("article", violations);

        StagedImages staged = new StagedImages(fileStorageService, "articles");
        List<String> superseded;
        try {
            String newFeaturedPath = featuredImage == null ? null : staged.store(featuredImage, "featured");
            List<String> newPaths = new ArrayList<>();
            for (int i = 0; i < newImages.size(); i++) {
                newPaths.add(staged.store(newImages.get(i), "image-" + i));
            }

            superseded = dslContext.transactionResult(cfg -> {
                DSLContext tx = DSL.using(cfg);
                SubmittedArticleRecord row = lockOwned(tx, accountId, articleId);
                requireChangesRequested(row);

                List<String> storedPaths = tx.select(SUBMITTED_ARTICLE_IMAGE.PATH)
                                             .from(SUBMITTED_ARTICLE_IMAGE)
                                             .where(SUBMITTED_ARTICLE_IMAGE.SUBMITTED_ARTICLE_ID.eq(articleId))
                                             .orderBy(SUBMITTED_ARTICLE_IMAGE.POSITION)
                                             .fetch(SUBMITTED_ARTICLE_IMAGE.PATH);
                ArticleOrderChecks.throwIfAny("article",
                                              paidForViolations(row, payload.body(), keptImages, storedPaths.size()));

                List<String> imagePaths = new ArrayList<>();
                Iterator<String> fresh = newPaths.iterator();
                for (Integer position : keptImages) {
                    imagePaths.add(position == null ? fresh.next() : storedPaths.get(position));
                }

                OffsetDateTime now = OffsetDateTime.now();
                tx.update(SUBMITTED_ARTICLE)
                  .set(SUBMITTED_ARTICLE.STATUS, SubmittedArticleStatus.PENDING_REVIEW.wire())
                  .set(SUBMITTED_ARTICLE.HEADLINE, payload.headline())
                  .set(SUBMITTED_ARTICLE.FEATURED_IMAGE_PATH,
                       newFeaturedPath == null ? row.getFeaturedImagePath() : newFeaturedPath)
                  .set(SUBMITTED_ARTICLE.BODY, ArticleJson.writeBody(payload.body()))
                  .set(SUBMITTED_ARTICLE.SOURCES, ArticleJson.write(payload.sources()))
                  .set(SUBMITTED_ARTICLE.CONSENTED_AT, now)
                  .set(SUBMITTED_ARTICLE.SUBMITTED_AT, now)
                  .where(SUBMITTED_ARTICLE.ID.eq(articleId))
                  .execute();
                tx.deleteFrom(SUBMITTED_ARTICLE_TOPIC)
                  .where(SUBMITTED_ARTICLE_TOPIC.SUBMITTED_ARTICLE_ID.eq(articleId))
                  .execute();
                tx.deleteFrom(SUBMITTED_ARTICLE_IMAGE)
                  .where(SUBMITTED_ARTICLE_IMAGE.SUBMITTED_ARTICLE_ID.eq(articleId))
                  .execute();
                writeTopicsAndImages(tx, articleId, payload.topics(), imagePaths);
                tx.update(SUBMITTED_ARTICLE_REVIEW_ROUND)
                  .set(SUBMITTED_ARTICLE_REVIEW_ROUND.RESUBMITTED_AT, now)
                  .where(SUBMITTED_ARTICLE_REVIEW_ROUND.SUBMITTED_ARTICLE_ID.eq(articleId))
                  .and(SUBMITTED_ARTICLE_REVIEW_ROUND.RESUBMITTED_AT.isNull())
                  .execute();

                List<String> dropped = new ArrayList<>(storedPaths);
                dropped.removeAll(imagePaths);
                if (newFeaturedPath != null) {
                    dropped.add(row.getFeaturedImagePath());
                }
                return dropped;
            });
        } catch (RuntimeException exception) {
            staged.discard();
            throw exception;
        }
        superseded.forEach(fileStorageService::delete);
        return get(accountId, articleId);
    }

    /**
     * What a revision's {@code keptImages} can be checked for before the stored images are read: each stored
     * image kept at most once, and a new part for every new image and no more.
     */
    static List<String> keptImageViolations(List<Integer> keptImages, int newImages) {
        List<String> violations = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        int fresh = 0;
        for (Integer position : keptImages) {
            if (position == null) {
                fresh++;
            } else if (position < 0 || !seen.add(position)) {
                violations.add("keptImages: Keep each stored image at most once");
            }
        }
        if (fresh != newImages) {
            violations.add("additionalImages: Send a file for each new image, " + fresh + " in all");
        }
        return violations;
    }

    /** The revision against the order as paid, and the stored images it keeps against those there are. */
    private static List<String> paidForViolations(SubmittedArticleRecord row, List<ArticleBlock> body,
                                                  List<Integer> keptImages, int storedImages) {
        List<String> violations = new ArrayList<>();
        if (keptImages.stream().anyMatch(position -> position != null && position >= storedImages)) {
            violations.add("keptImages: The article has " + count(storedImages, "stored image"));
        }
        if (ArticleBodyRules.bodyLinks(body).size() > row.getLinks()) {
            violations.add("body: Your order includes " + count(row.getLinks(), "follow link")
                           + ", so the article can carry no more");
        }
        if (keptImages.size() > row.getImages()) {
            violations.add("additionalImages: Your order includes " + count(row.getImages(), "additional image")
                           + ", so the article can carry no more");
        }
        return violations;
    }

    private static String count(int n, String noun) {
        return n + " " + noun + (n == 1 ? "" : "s");
    }

    /** What submitting and revising check alike: the topics, the body, and how many images it holds. */
    private List<String> checkContent(List<String> topics, List<ArticleBlock> body, int images) {
        List<String> violations = new ArrayList<>();
        checks.checkTopics(topics, violations);
        violations.addAll(ArticleBodyRules.checkBody(body, images));
        if (images > MAX_IMAGES) {
            violations.add("additionalImages: Add at most " + MAX_IMAGES + " images to the article");
        }
        return violations;
    }

    private static void writeTopicsAndImages(DSLContext tx, long articleId, List<String> topics,
                                             List<String> imagePaths) {
        for (String topic : new LinkedHashSet<>(topics)) {
            tx.insertInto(SUBMITTED_ARTICLE_TOPIC)
              .set(SUBMITTED_ARTICLE_TOPIC.SUBMITTED_ARTICLE_ID, articleId)
              .set(SUBMITTED_ARTICLE_TOPIC.TOPIC, topic)
              .execute();
        }
        for (int i = 0; i < imagePaths.size(); i++) {
            tx.insertInto(SUBMITTED_ARTICLE_IMAGE)
              .set(SUBMITTED_ARTICLE_IMAGE.SUBMITTED_ARTICLE_ID, articleId)
              .set(SUBMITTED_ARTICLE_IMAGE.POSITION, i)
              .set(SUBMITTED_ARTICLE_IMAGE.PATH, imagePaths.get(i))
              .execute();
        }
    }

    /** Locked so two revisions of the same article - two tabs, a double click - apply one after the other. */
    private static SubmittedArticleRecord lockOwned(DSLContext tx, long accountId, long articleId) {
        SubmittedArticleRecord row = tx.selectFrom(SUBMITTED_ARTICLE)
                                       .where(SUBMITTED_ARTICLE.ID.eq(articleId))
                                       .and(SUBMITTED_ARTICLE.ACCOUNT_ID.eq(accountId))
                                       .forUpdate()
                                       .fetchOne();
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No submitted article with id " + articleId);
        }
        return row;
    }

    private static void requireChangesRequested(SubmittedArticleRecord row) {
        SubmittedArticleStatus status = SubmittedArticleStatus.fromWire(row.getStatus());
        if (status != SubmittedArticleStatus.CHANGES_REQUESTED) {
            String detail = status == SubmittedArticleStatus.PENDING_REVIEW
                    ? "This article is waiting for review and can't be changed."
                    : "This article has been approved and can't be changed.";
            throw new ResponseStatusException(HttpStatus.CONFLICT, detail);
        }
    }
}
