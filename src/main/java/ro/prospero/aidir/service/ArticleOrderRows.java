package ro.prospero.aidir.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.SortField;
import org.springframework.stereotype.Component;
import ro.prospero.aidir.data.ArticleOrderDTO;
import ro.prospero.aidir.data.ArticleOrderKind;
import ro.prospero.aidir.data.ArticleOrderStatus;
import ro.prospero.aidir.data.ArticleQuoteDTO;
import ro.prospero.aidir.data.WritingTier;
import ro.prospero.aidir.jooq.generated.public_.tables.records.ArticleOrderRecord;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static ro.prospero.aidir.jooq.generated.public_.Tables.ARTICLE_ORDER;
import static ro.prospero.aidir.jooq.generated.public_.Tables.ARTICLE_ORDER_IMAGE;
import static ro.prospero.aidir.jooq.generated.public_.Tables.ARTICLE_ORDER_TOPIC;

/**
 * The article order tables, read into {@link ArticleOrderDTO}, and the JSON the list and quote columns hold.
 * The vendor and review services both go through here, so an order reads the same wherever it is shown.
 *
 * <p>Every method that queries takes the DSLContext to run on, so callers can use it inside a transaction.
 */
@Component
public class ArticleOrderRows {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {
    };

    private final MediaUrls mediaUrls;

    public ArticleOrderRows(MediaUrls mediaUrls) {
        this.mediaUrls = mediaUrls;
    }

    /** An order with the account it belongs to, which the DTO leaves out. */
    public record StoredOrder(long accountId, ArticleOrderDTO order) {
    }

    public List<StoredOrder> read(DSLContext ctx, Condition condition, SortField<?>... order) {
        List<ArticleOrderRecord> orders = ctx.selectFrom(ARTICLE_ORDER).where(condition).orderBy(order).fetch();
        if (orders.isEmpty()) {
            return List.of();
        }
        List<Long> ids = orders.stream().map(ArticleOrderRecord::getId).toList();

        // One query each for the topics and images of every order read, not one per order.
        Map<Long, List<String>> topics =
                ctx.select(ARTICLE_ORDER_TOPIC.ARTICLE_ORDER_ID, ARTICLE_ORDER_TOPIC.TOPIC)
                   .from(ARTICLE_ORDER_TOPIC)
                   .where(ARTICLE_ORDER_TOPIC.ARTICLE_ORDER_ID.in(ids))
                   .orderBy(ARTICLE_ORDER_TOPIC.TOPIC)
                   .fetchGroups(ARTICLE_ORDER_TOPIC.ARTICLE_ORDER_ID, ARTICLE_ORDER_TOPIC.TOPIC);
        Map<Long, List<String>> images =
                ctx.select(ARTICLE_ORDER_IMAGE.ARTICLE_ORDER_ID, ARTICLE_ORDER_IMAGE.PATH)
                   .from(ARTICLE_ORDER_IMAGE)
                   .where(ARTICLE_ORDER_IMAGE.ARTICLE_ORDER_ID.in(ids))
                   .orderBy(ARTICLE_ORDER_IMAGE.POSITION)
                   .fetchGroups(ARTICLE_ORDER_IMAGE.ARTICLE_ORDER_ID, ARTICLE_ORDER_IMAGE.PATH);

        return orders.stream()
                     .map(row -> new StoredOrder(row.getAccountId(),
                                                 toDto(row,
                                                       topics.getOrDefault(row.getId(), List.of()),
                                                       images.getOrDefault(row.getId(), List.of()))))
                     .toList();
    }

    public Optional<StoredOrder> readOne(DSLContext ctx, Condition condition) {
        return read(ctx, condition).stream().findFirst();
    }

    static JSONB writeJson(Object value) {
        try {
            return JSONB.valueOf(JSON_MAPPER.writeValueAsString(value));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise " + value, e);
        }
    }

    private ArticleOrderDTO toDto(ArticleOrderRecord row, List<String> topics, List<String> imagePaths) {
        ArticleOrderKind kind = ArticleOrderKind.fromWire(row.getKind());
        ArticleOrderDTO.Submitted submitted = kind == ArticleOrderKind.SUBMITTED
                ? new ArticleOrderDTO.Submitted(row.getBody(), imagePaths.stream().map(mediaUrls::publicUrl).toList())
                : null;
        ArticleOrderDTO.Editorial editorial = kind == ArticleOrderKind.EDITORIAL
                ? new ArticleOrderDTO.Editorial(row.getBrief(), row.getAudience(), readStrings(row.getKeyMessages()),
                                                row.getPreferredDate(), WritingTier.fromWire(row.getWritingTier()))
                : null;

        return new ArticleOrderDTO(row.getId(),
                                   reference(row),
                                   kind,
                                   ArticleOrderStatus.fromWire(row.getStatus()),
                                   row.getHeadline(),
                                   topics,
                                   row.getFeaturedImagePath() == null ? null
                                           : mediaUrls.publicUrl(row.getFeaturedImagePath()),
                                   readStrings(row.getSources()),
                                   submitted,
                                   editorial,
                                   new ArticleOrderDTO.Options(row.getLinks(), row.getPermanentLinks(),
                                                               row.getUnlabelled()),
                                   readQuote(row.getQuote()),
                                   row.getRejectionReason(),
                                   row.getCreatedAt(),
                                   row.getReviewedAt(),
                                   row.getPaidAt());
    }

    /** The format the success page has always shown: {@code AIDP-2026-0042}. */
    private static String reference(ArticleOrderRecord row) {
        return "AIDP-%d-%04d".formatted(row.getCreatedAt().getYear(), row.getId());
    }

    private static List<String> readStrings(JSONB jsonb) {
        if (jsonb == null) {
            return List.of();
        }
        try {
            return JSON_MAPPER.readValue(jsonb.data(), STRINGS);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Malformed string list in article_order: " + jsonb.data(), e);
        }
    }

    private static ArticleQuoteDTO readQuote(JSONB jsonb) {
        try {
            return JSON_MAPPER.readValue(jsonb.data(), ArticleQuoteDTO.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Malformed quote in article_order: " + jsonb.data(), e);
        }
    }
}
