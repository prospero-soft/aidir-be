package ro.prospero.aidir.service;

import ro.prospero.aidir.data.ArticleQuoteDTO;
import ro.prospero.aidir.data.ArticleQuoteDTO.Line;
import ro.prospero.aidir.data.WritingTier;

import java.util.ArrayList;
import java.util.List;

/**
 * What one sponsored article costs, in whole euros. A port of {@code quote()} in aidir-fe's
 * src/data/newsPricing.ts together with {@code articleLines()} in src/components/article-submission/pricing.ts:
 * the prices, the order of the lines and their labels are the same, so the quote stored with an order reads
 * exactly like the order summary the vendor accepted. A price changed on one side has to change on the other.
 */
public final class ArticlePricing {
    static final int BASE = 79;
    static final int EXTRA_LINK = 23;
    static final int PERMANENT_LINKS = 45;
    static final int UNLABELLED = 12;
    static final int ADDITIONAL_IMAGE = 10;
    public static final int MAX_LINKS = 3;

    /** Ascending by threshold; an order gets the highest tier its subtotal reaches. */
    private static final List<DiscountTier> DISCOUNT_TIERS = List.of(new DiscountTier(1250, 10),
                                                                     new DiscountTier(2500, 15),
                                                                     new DiscountTier(3750, 20));

    private record DiscountTier(int threshold, int percent) {
    }

    private ArticlePricing() {
    }

    /**
     * @param links            the follow links billed, 1 to {@link #MAX_LINKS}
     * @param writing          the article length our editors write, or null when the vendor wrote it
     * @param additionalImages images beyond the featured one
     */
    public static ArticleQuoteDTO quote(int links, WritingTier writing, boolean permanentLinks, boolean unlabelled,
                                        int additionalImages) {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line("Sponsored article - 1 follow EML link", BASE));
        int extraLinks = links - 1;
        if (extraLinks > 0) {
            lines.add(new Line("Additional follow EML links ×" + extraLinks, extraLinks * EXTRA_LINK));
        }
        if (writing != null) {
            lines.add(writingLine(writing));
        }
        if (permanentLinks) {
            lines.add(new Line("Permanent follow links", PERMANENT_LINKS));
        }
        if (unlabelled) {
            lines.add(new Line("Publish without sponsored label", UNLABELLED));
        }
        if (additionalImages > 0) {
            lines.add(new Line("Additional images ×" + additionalImages, additionalImages * ADDITIONAL_IMAGE));
        }

        int subtotal = lines.stream().mapToInt(Line::amount).sum();
        int discount = discountFor(subtotal);
        return new ArticleQuoteDTO(List.copyOf(lines), subtotal, discount, subtotal - discount);
    }

    /** Rounded half up, as the front end's Math.round does. */
    static int discountFor(int subtotal) {
        int percent = 0;
        for (DiscountTier tier : DISCOUNT_TIERS) {
            if (subtotal >= tier.threshold()) {
                percent = tier.percent();
            }
        }
        return (int) Math.round(subtotal * percent / 100.0);
    }

    private static Line writingLine(WritingTier writing) {
        return switch (writing) {
            case SHORT -> new Line("Short article writing (500–800 words)", 99);
            case STANDARD -> new Line("Standard article writing (800–1,200 words)", 149);
            case EXTENDED -> new Line("Extended article writing (1,200–1,800 words)", 199);
        };
    }
}
