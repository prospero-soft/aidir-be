package ro.prospero.aidir.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The checks on a vendor's article body that Bean Validation cannot express: its length and the follow
 * links written into it, which are also what the article is billed for. Ports of {@code wordCount},
 * {@code bodyLinks} and {@code isHttpUrl} in aidir-fe's src/components/article-submission/validation.ts,
 * so an article the validation panel passed is not refused here.
 *
 * <p>Each violation reads {@code body: <message>}, with the message written for the vendor.
 */
public final class ArticleOrderRules {
    /** MIN_ARTICLE_WORDS in src/components/article-submission/constants.ts. */
    static final int MIN_WORDS = 500;

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern LINK = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[.,;:!?)\\]'\"]+$");

    private ArticleOrderRules() {
    }

    public static List<String> checkBody(String body) {
        List<String> violations = new ArrayList<>();
        int shortfall = MIN_WORDS - wordCount(body);
        if (shortfall > 0) {
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
        return violations;
    }

    /** The follow links billed: the links in the body, with the one the base price includes as the floor. */
    public static int billedLinks(String body) {
        return Math.min(Math.max(bodyLinks(body).size(), 1), ArticlePricing.MAX_LINKS);
    }

    static int wordCount(String text) {
        String trimmed = text.trim();
        return trimmed.isEmpty() ? 0 : WHITESPACE.split(trimmed).length;
    }

    /** The distinct http(s) links in the body, in order, minus the punctuation a sentence may close them with. */
    static List<String> bodyLinks(String body) {
        Set<String> links = new LinkedHashSet<>();
        Matcher matcher = LINK.matcher(body);
        while (matcher.find()) {
            links.add(TRAILING_PUNCTUATION.matcher(matcher.group()).replaceFirst(""));
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

    private static String plural(int n, String word) {
        return n + " " + word + (n == 1 ? "" : "s");
    }
}
