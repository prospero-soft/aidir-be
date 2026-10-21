package ro.prospero.aidir.service;

import ro.prospero.aidir.data.ArticleBlock;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The checks on a vendor's article body that Bean Validation cannot express: its length, the follow links
 * in it, which are also what the article is billed for, and what its template asks of its pull quotes and
 * images. Ports of aidir-fe's src/components/article-submission/body.ts and of the body checks in
 * validation.ts, so an article the validation panel passed is not refused here.
 *
 * <p>Each violation reads {@code body: <message>}, with the message written for the vendor.
 */
public final class ArticleBodyRules {
    /** MIN_ARTICLE_WORDS in src/components/article-submission/constants.ts. */
    static final int MIN_WORDS = 500;

    /** MAX_ARTICLE_CHARACTERS in src/components/article-submission/constants.ts. */
    static final int MAX_CHARACTERS = 20_000;

    /** How many pull quotes an article of fewer than {@code belowWords} words can carry. */
    record PullQuoteBand(int belowWords, int max) {
    }

    /** STANDARD_TEMPLATE.pullQuotes in src/components/article-submission/template.ts. */
    static final List<PullQuoteBand> PULL_QUOTE_BANDS = List.of(new PullQuoteBand(1000, 1),
                                                                new PullQuoteBand(2000, 2),
                                                                new PullQuoteBand(Integer.MAX_VALUE, 3));

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private ArticleBodyRules() {
    }

    /** @param images the number of in-article image parts the request carried */
    public static List<String> checkBody(List<ArticleBlock> body, int images) {
        List<String> violations = new ArrayList<>();
        int words = wordCount(body);
        int excess = characterCount(body) - MAX_CHARACTERS;
        int shortfall = MIN_WORDS - words;
        if (excess > 0) {
            violations.add("body: Cut " + plural(excess, "character") + " - an article can be at most "
                           + MAX_CHARACTERS + " characters long");
        } else if (shortfall > 0) {
            violations.add("body: Add at least " + plural(shortfall, "more word")
                           + " to meet the minimum content length");
        }

        List<String> links = bodyLinks(body);
        List<String> broken = links.stream().filter(link -> !isHttpUrl(link)).toList();
        if (!broken.isEmpty()) {
            violations.add("body: Check " + String.join(", ", broken) + " - not a valid link");
        } else if (links.size() > ArticlePricing.MAX_LINKS) {
            violations.add("body: An article can carry at most " + ArticlePricing.MAX_LINKS + " follow links");
        }

        int maxPullQuotes = maxPullQuotes(words);
        if (body.stream().filter(ArticleBlock.PullQuote.class::isInstance).count() > maxPullQuotes) {
            violations.add("body: An article of " + plural(words, "word") + " carries at most "
                           + plural(maxPullQuotes, "pull quote"));
        }

        violations.addAll(checkImageNumbering(body, images));
        return violations;
    }

    /** The editor numbers its images in the order they appear, one image each. */
    public static List<String> checkImageNumbering(List<ArticleBlock> body, int images) {
        List<Integer> numbered = body.stream()
                                     .filter(ArticleBlock.Image.class::isInstance)
                                     .map(block -> ((ArticleBlock.Image) block).image())
                                     .toList();
        if (!numbered.equals(IntStream.range(0, images).boxed().toList())) {
            return List.of("body: The images in the article do not match the images sent with it");
        }
        return List.of();
    }

    /** The follow links billed: the links in the body, with the one the base price includes as the floor. */
    public static int billedLinks(List<ArticleBlock> body) {
        return Math.min(Math.max(bodyLinks(body).size(), 1), ArticlePricing.MAX_LINKS);
    }

    static int maxPullQuotes(int words) {
        return PULL_QUOTE_BANDS.stream().filter(band -> words < band.belowWords()).findFirst().orElseThrow().max();
    }

    /** Every character a reader sees, pull quotes included; alt text is capped on its own. */
    static int characterCount(List<ArticleBlock> body) {
        return body.stream()
                   .mapToInt(block -> switch (block) {
                       case ArticleBlock.Paragraph p -> text(p.content()).length();
                       case ArticleBlock.Heading h -> h.text().length();
                       case ArticleBlock.PullQuote q -> q.text().length();
                       case ArticleBlock.ListBlock l -> l.items().stream().mapToInt(item -> text(item).length()).sum();
                       case ArticleBlock.Image i -> 0;
                   })
                   .sum();
    }

    /** The words of the article's own text: a pull quote repeats it and alt text describes an image. */
    static int wordCount(List<ArticleBlock> body) {
        return wordCount(body.stream()
                             .map(block -> switch (block) {
                                 case ArticleBlock.Paragraph p -> text(p.content());
                                 case ArticleBlock.Heading h -> h.text();
                                 case ArticleBlock.ListBlock l -> l.items().stream()
                                                                   .map(ArticleBodyRules::text)
                                                                   .collect(Collectors.joining("\n"));
                                 case ArticleBlock.PullQuote q -> "";
                                 case ArticleBlock.Image i -> "";
                             })
                             .collect(Collectors.joining("\n")));
    }

    static int wordCount(String text) {
        String trimmed = text.trim();
        return trimmed.isEmpty() ? 0 : WHITESPACE.split(trimmed).length;
    }

    /** The distinct link targets in the body, in the order they first appear. */
    static List<String> bodyLinks(List<ArticleBlock> body) {
        Set<String> links = new LinkedHashSet<>();
        for (ArticleBlock block : body) {
            List<ArticleBlock.Inline> runs = switch (block) {
                case ArticleBlock.Paragraph p -> p.content();
                case ArticleBlock.ListBlock l -> l.items().stream().flatMap(List::stream).toList();
                default -> List.of();
            };
            runs.stream()
                .map(ArticleBlock.Inline::href)
                .filter(href -> href != null && !href.isEmpty())
                .forEach(links::add);
        }
        return List.copyOf(links);
    }

    static boolean isHttpUrl(String value) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            return (scheme.equals("http") || scheme.equals("https"))
                   && uri.getHost() != null && uri.getHost().contains(".");
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static String text(List<ArticleBlock.Inline> runs) {
        return runs.stream().map(ArticleBlock.Inline::text).collect(Collectors.joining());
    }

    private static String plural(int n, String word) {
        return n + " " + word + (n == 1 ? "" : "s");
    }
}
