package ro.prospero.aidir.service;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import ro.prospero.aidir.data.ArticleOrderDTO;
import ro.prospero.aidir.data.ArticleOrderKind;
import ro.prospero.aidir.data.ArticleQuoteDTO;
import ro.prospero.aidir.data.ArticleRequestPayload;
import ro.prospero.aidir.data.ArticleSubmissionPayload;
import ro.prospero.aidir.exception.ApiPayloadValidationException;
import ro.prospero.aidir.jooq.generated.public_.tables.records.ArticleOrderRecord;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static ro.prospero.aidir.jooq.generated.public_.Tables.ARTICLE_ORDER;
import static ro.prospero.aidir.jooq.generated.public_.Tables.ARTICLE_ORDER_IMAGE;
import static ro.prospero.aidir.jooq.generated.public_.Tables.ARTICLE_ORDER_TOPIC;
import static ro.prospero.aidir.jooq.generated.public_.Tables.NEWS_TOPIC;

/**
 * A vendor's sponsored article orders: placing one of either kind, and reading back their own.
 *
 * <p>An order is placed in one go - there are no drafts - and is priced here by {@link ArticlePricing}, not
 * by what the client says it costs. The client's total is only compared against ours, so a vendor is never
 * billed a price other than the one they accepted. Every read is scoped by the account as well as the id,
 * so another vendor's order answers as one that does not exist.
 *
 * <p>Images are stored before the transaction, because a submitted article's row cannot be inserted without
 * its featured image, and are removed again if the transaction fails. Their directory is therefore a fresh
 * random one rather than the order id, which does not exist yet.
 */
@Service
public class ArticleOrderService {
    private static final String ARTICLE_DIR = "articles/";

    /**
     * Raster images only, as for deal logos: /media serves from our own origin and a vendor-supplied SVG can
     * carry script. The limits are those of the article form (src/components/article-submission/constants.ts).
     */
    private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp");
    static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    static final int MAX_ADDITIONAL_IMAGES = 4;

    private final DSLContext dslContext;
    private final ArticleOrderRows orderRows;
    private final FileStorageService fileStorageService;

    public ArticleOrderService(DSLContext dslContext, ArticleOrderRows orderRows,
                               FileStorageService fileStorageService) {
        this.dslContext = dslContext;
        this.orderRows = orderRows;
        this.fileStorageService = fileStorageService;
    }

    public List<ArticleOrderDTO> list(long accountId) {
        return orderRows.read(dslContext, ARTICLE_ORDER.ACCOUNT_ID.eq(accountId), ARTICLE_ORDER.CREATED_AT.desc())
                        .stream()
                        .map(ArticleOrderRows.StoredOrder::order)
                        .toList();
    }

    public ArticleOrderDTO get(long accountId, long orderId) {
        return orderRows.readOne(dslContext, ARTICLE_ORDER.ID.eq(orderId).and(ARTICLE_ORDER.ACCOUNT_ID.eq(accountId)))
                        .map(ArticleOrderRows.StoredOrder::order)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                                       "No article order with id " + orderId));
    }

    /**
     * An article the vendor wrote. The links billed are the ones written into the body.
     *
     * @param featuredImage    required; null when the request carried none
     * @param additionalImages in the order the vendor added them
     */
    public ArticleOrderDTO submitArticle(long accountId, ArticleSubmissionPayload payload,
                                         MultipartFile featuredImage, List<MultipartFile> additionalImages) {
        List<String> violations = new ArrayList<>();
        checkTopics(payload.topics(), violations);
        violations.addAll(ArticleOrderRules.checkBody(payload.body()));
        if (featuredImage == null) {
            violations.add("featuredImage: Add the image to use as the article cover");
        } else {
            checkImage("featuredImage", featuredImage, violations);
        }
        if (additionalImages.size() > MAX_ADDITIONAL_IMAGES) {
            violations.add("additionalImages: Add at most " + MAX_ADDITIONAL_IMAGES + " additional images");
        }
        for (int i = 0; i < additionalImages.size(); i++) {
            checkImage("additionalImages[" + i + "]", additionalImages.get(i), violations);
        }
        throwIfAny(violations);

        ArticleSubmissionPayload.Options options = payload.options();
        int links = ArticleOrderRules.billedLinks(payload.body());
        ArticleQuoteDTO quote = ArticlePricing.quote(links, null, options.permanentLinks(), options.unlabelled(),
                                                     additionalImages.size());
        requireQuoted(payload.quotedTotal(), quote);

        return place(accountId, new NewOrder(ArticleOrderKind.SUBMITTED, payload.headline(), payload.topics(),
                                             payload.sources(), links, options.permanentLinks(),
                                             options.unlabelled(), quote, featuredImage, additionalImages,
                                             row -> row.setBody(payload.body())));
    }

    /** A brief for an article our editors write. The links billed are the ones the vendor chose. */
    public ArticleOrderDTO requestArticle(long accountId, ArticleRequestPayload payload, MultipartFile featuredImage) {
        List<String> violations = new ArrayList<>();
        checkTopics(payload.topics(), violations);
        if (featuredImage != null) {
            checkImage("featuredImage", featuredImage, violations);
        }
        throwIfAny(violations);

        ArticleRequestPayload.Options options = payload.options();
        ArticleQuoteDTO quote = ArticlePricing.quote(options.links(), options.writing(), options.permanentLinks(),
                                                     options.unlabelled(), 0);
        requireQuoted(payload.quotedTotal(), quote);

        String audience = payload.audience() == null || payload.audience().isBlank() ? null : payload.audience();
        return place(accountId, new NewOrder(ArticleOrderKind.EDITORIAL, payload.headline(), payload.topics(),
                                             payload.sources(), options.links(), options.permanentLinks(),
                                             options.unlabelled(), quote, featuredImage, List.of(),
                                             row -> {
                                                 row.setBrief(payload.brief());
                                                 row.setAudience(audience);
                                                 row.setKeyMessages(ArticleOrderRows.writeJson(payload.keyMessages()));
                                                 row.setPreferredDate(payload.preferredDate());
                                                 row.setWritingTier(options.writing().wire());
                                             }));
    }

    /** What both kinds of order have in common, and the columns only {@code kind} has. */
    private record NewOrder(ArticleOrderKind kind,
                            String headline,
                            List<String> topics,
                            List<String> sources,
                            int links,
                            boolean permanentLinks,
                            boolean unlabelled,
                            ArticleQuoteDTO quote,
                            MultipartFile featuredImage,
                            List<MultipartFile> additionalImages,
                            Consumer<ArticleOrderRecord> kindColumns) {
    }

    private ArticleOrderDTO place(long accountId, NewOrder order) {
        String dir = ARTICLE_DIR + UUID.randomUUID();
        List<String> storedPaths = new ArrayList<>();
        long orderId;
        try {
            String featuredPath = order.featuredImage() == null ? null
                    : store(order.featuredImage(), dir, "featured", storedPaths);
            List<String> additionalPaths = new ArrayList<>();
            for (int i = 0; i < order.additionalImages().size(); i++) {
                additionalPaths.add(store(order.additionalImages().get(i), dir, "image-" + i, storedPaths));
            }

            orderId = dslContext.transactionResult(cfg -> {
                DSLContext tx = DSL.using(cfg);
                ArticleOrderRecord row = tx.newRecord(ARTICLE_ORDER);
                row.setAccountId(accountId);
                row.setKind(order.kind().wire());
                row.setHeadline(order.headline());
                row.setFeaturedImagePath(featuredPath);
                row.setSources(ArticleOrderRows.writeJson(order.sources()));
                row.setLinks(order.links());
                row.setPermanentLinks(order.permanentLinks());
                row.setUnlabelled(order.unlabelled());
                row.setQuote(ArticleOrderRows.writeJson(order.quote()));
                row.setTotalEur(order.quote().total());
                row.setConsentedAt(OffsetDateTime.now());
                order.kindColumns().accept(row);
                row.insert();

                for (String topic : new LinkedHashSet<>(order.topics())) {
                    tx.insertInto(ARTICLE_ORDER_TOPIC)
                      .set(ARTICLE_ORDER_TOPIC.ARTICLE_ORDER_ID, row.getId())
                      .set(ARTICLE_ORDER_TOPIC.TOPIC, topic)
                      .execute();
                }
                for (int i = 0; i < additionalPaths.size(); i++) {
                    tx.insertInto(ARTICLE_ORDER_IMAGE)
                      .set(ARTICLE_ORDER_IMAGE.ARTICLE_ORDER_ID, row.getId())
                      .set(ARTICLE_ORDER_IMAGE.POSITION, i)
                      .set(ARTICLE_ORDER_IMAGE.PATH, additionalPaths.get(i))
                      .execute();
                }
                return row.getId();
            });
        } catch (RuntimeException exception) {
            storedPaths.forEach(fileStorageService::delete);
            throw exception;
        }
        return get(accountId, orderId);
    }

    /** A random suffix, so a URL guessed from one order's image names leads nowhere in another's. */
    private String store(MultipartFile image, String dir, String baseName, List<String> storedPaths) {
        String path = fileStorageService.storeImage(image, dir,
                                                    baseName + "-" + UUID.randomUUID().toString().substring(0, 8));
        storedPaths.add(path);
        return path;
    }

    private void checkTopics(List<String> topics, List<String> violations) {
        Set<String> known = new HashSet<>(dslContext.fetchValues(NEWS_TOPIC.NAME));
        topics.stream()
              .filter(topic -> !known.contains(topic))
              .forEach(topic -> violations.add("topics: unknown topic '" + topic + "'"));
    }

    private static void checkImage(String field, MultipartFile image, List<String> violations) {
        String type = image.getContentType();
        if (type == null || !IMAGE_TYPES.contains(type.toLowerCase(Locale.ROOT).trim())) {
            violations.add(field + ": Use a PNG, JPG or WebP image");
        } else if (image.getSize() > MAX_IMAGE_BYTES) {
            violations.add(field + ": Use an image of up to 5MB");
        }
    }

    private static void requireQuoted(int quotedTotal, ArticleQuoteDTO quote) {
        if (quotedTotal != quote.total()) {
            throw new ApiPayloadValidationException(
                    "The price has changed - review your order again.",
                    List.of("quotedTotal: The order costs €" + quote.total() + ", not €" + quotedTotal));
        }
    }

    private static void throwIfAny(List<String> violations) {
        if (!violations.isEmpty()) {
            throw new ApiPayloadValidationException("Validating the article order failed.", violations);
        }
    }
}
