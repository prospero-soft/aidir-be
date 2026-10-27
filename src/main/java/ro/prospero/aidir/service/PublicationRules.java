package ro.prospero.aidir.service;

import ro.prospero.aidir.data.ArticleBlock;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** What publishing an article works out from it: its address, its listing excerpt, and when it goes live. */
public final class PublicationRules {
    static final int MAX_SLUG_LENGTH = 80;
    static final int MAX_EXCERPT_LENGTH = 200;
    static final int WORDS_PER_MINUTE = 200;
    static final int FOLLOW_LINK_MONTHS = 12;

    /** The slug of a headline with nothing in it a URL can carry, such as one written in another script. */
    static final String FALLBACK_SLUG = "article";

    /** The front end's own pages under /news/, which an article's address must not shadow. */
    static final Set<String> RESERVED_SLUGS = Set.of("pricing", "submit", "request");

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NOT_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private PublicationRules() {
    }

    /** Lower-case ASCII words joined by hyphens, cut between words to at most {@link #MAX_SLUG_LENGTH}. */
    public static String slug(String headline) {
        String ascii = MARKS.matcher(Normalizer.normalize(headline, Normalizer.Form.NFD)).replaceAll("");
        String slug = trimHyphens(NOT_ALPHANUMERIC.matcher(ascii.toLowerCase(Locale.ROOT)).replaceAll("-"));
        if (slug.length() > MAX_SLUG_LENGTH) {
            String cut = slug.substring(0, MAX_SLUG_LENGTH + 1);
            int lastHyphen = cut.lastIndexOf('-');
            slug = trimHyphens(lastHyphen > 0 ? cut.substring(0, lastHyphen) : slug.substring(0, MAX_SLUG_LENGTH));
        }
        return slug.isEmpty() ? FALLBACK_SLUG : slug;
    }

    /**
     * {@code base} if no article has it yet and no page of the front end uses it, otherwise the first of
     * {@code base-2}, {@code base-3}... free.
     */
    public static String uniqueSlug(String base, Set<String> taken) {
        if (!taken.contains(base) && !RESERVED_SLUGS.contains(base)) {
            return base;
        }
        int n = 2;
        while (taken.contains(base + "-" + n)) {
            n++;
        }
        return base + "-" + n;
    }

    /** The first paragraph's text, cut between words to about {@link #MAX_EXCERPT_LENGTH} characters. */
    public static String excerpt(List<ArticleBlock> body) {
        String text = body.stream()
                          .filter(ArticleBlock.Paragraph.class::isInstance)
                          .map(block -> ((ArticleBlock.Paragraph) block).content().stream()
                                                                        .map(ArticleBlock.Inline::text)
                                                                        .collect(Collectors.joining()))
                          .map(paragraph -> WHITESPACE.matcher(paragraph).replaceAll(" ").trim())
                          .filter(paragraph -> !paragraph.isEmpty())
                          .findFirst()
                          .orElse("");
        if (text.length() <= MAX_EXCERPT_LENGTH) {
            return text;
        }
        String cut = text.substring(0, MAX_EXCERPT_LENGTH);
        int lastSpace = cut.lastIndexOf(' ');
        return (lastSpace > 0 ? cut.substring(0, lastSpace) : cut).stripTrailing() + "…";
    }

    public static int readMinutes(List<ArticleBlock> body) {
        int words = ArticleBodyRules.wordCount(body);
        return Math.max(1, (words + WORDS_PER_MINUTE - 1) / WORDS_PER_MINUTE);
    }

    /**
     * A completed brief goes live straight away, or at the start of the day the vendor asked for if that is
     * still to come. The day starts in {@code zone}; which zone that should be is an open question shared
     * with deal scheduling.
     */
    public static OffsetDateTime briefPublishedAt(OffsetDateTime now, LocalDate preferredDate, ZoneId zone) {
        if (preferredDate == null) {
            return now;
        }
        OffsetDateTime preferred = preferredDate.atStartOfDay(zone).toOffsetDateTime();
        return preferred.isAfter(now) ? preferred : now;
    }

    /** Null when the order bought permanent links: they never stop being follow links. */
    public static OffsetDateTime followLinksUntil(OffsetDateTime publishedAt, boolean permanentLinks) {
        return permanentLinks ? null : publishedAt.plusMonths(FOLLOW_LINK_MONTHS);
    }

    public static boolean followLinks(OffsetDateTime followLinksUntil, OffsetDateTime now) {
        return followLinksUntil == null || followLinksUntil.isAfter(now);
    }

    private static String trimHyphens(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '-') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '-') {
            end--;
        }
        return value.substring(start, end);
    }
}
