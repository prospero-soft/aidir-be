package ro.prospero.aidir.service;

import org.jooq.DSLContext;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import ro.prospero.aidir.data.ArticleQuoteDTO;
import ro.prospero.aidir.exception.ApiPayloadValidationException;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static ro.prospero.aidir.jooq.generated.Tables.NEWS_TOPIC;

/**
 * What placing a submitted article and placing a brief check alike: the topics against the news vocabulary,
 * the images, and the client's total against our own price.
 */
@Component
public class ArticleOrderChecks {
    /**
     * Raster images only, as for deal logos: /media serves from our own origin and a vendor-supplied SVG can
     * carry script. The limits are those of the article form (src/components/article-submission/constants.ts).
     */
    private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp");
    static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;

    private final DSLContext dslContext;

    public ArticleOrderChecks(DSLContext dslContext) {
        this.dslContext = dslContext;
    }

    void checkTopics(List<String> topics, List<String> violations) {
        Set<String> known = new HashSet<>(dslContext.fetchValues(NEWS_TOPIC.NAME));
        topics.stream()
              .filter(topic -> !known.contains(topic))
              .forEach(topic -> violations.add("topics: unknown topic '" + topic + "'"));
    }

    static void checkImage(String field, MultipartFile image, List<String> violations) {
        String type = image.getContentType();
        if (type == null || !IMAGE_TYPES.contains(type.toLowerCase(Locale.ROOT).trim())) {
            violations.add(field + ": Use a PNG, JPG or WebP image");
        } else if (image.getSize() > MAX_IMAGE_BYTES) {
            violations.add(field + ": Use an image of up to 5MB");
        }
    }

    /** The client's total is only compared with ours, so nobody is billed a price other than the one accepted. */
    static void requireQuoted(int quotedTotal, ArticleQuoteDTO quote) {
        if (quotedTotal != quote.total()) {
            throw new ApiPayloadValidationException(
                    "The price has changed - review your order again.",
                    List.of("quotedTotal: The order costs €" + quote.total() + ", not €" + quotedTotal));
        }
    }

    /** @param subject what was checked, for the message: "article" or "brief" */
    static void throwIfAny(String subject, List<String> violations) {
        if (!violations.isEmpty()) {
            throw new ApiPayloadValidationException("Validating the " + subject + " failed.", violations);
        }
    }
}
