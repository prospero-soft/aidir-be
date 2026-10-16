package ro.prospero.aidir.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArticleOrderRulesTest {

    private static String words(int n) {
        return "word ".repeat(n);
    }

    @Test
    void wordsAreSplitOnAnyWhitespace() {
        assertThat(ArticleOrderRules.wordCount("  one\ttwo\n\nthree  ")).isEqualTo(3);
        assertThat(ArticleOrderRules.wordCount("   ")).isZero();
    }

    @Test
    void linksAreDistinctAndLoseTheirClosingPunctuation() {
        String body = "See https://acme.example/a. Or (https://acme.example/b), and https://acme.example/a again.";

        assertThat(ArticleOrderRules.bodyLinks(body))
                .containsExactly("https://acme.example/a", "https://acme.example/b");
    }

    @Test
    void theBilledLinksAreAtLeastOneAndAtMostThree() {
        assertThat(ArticleOrderRules.billedLinks("no links at all")).isEqualTo(1);
        assertThat(ArticleOrderRules.billedLinks("https://a.example https://b.example")).isEqualTo(2);
        assertThat(ArticleOrderRules.billedLinks("https://a.example https://b.example https://c.example "
                                                 + "https://d.example")).isEqualTo(3);
    }

    @Test
    void aLongEnoughBodyWithFewLinksPasses() {
        assertThat(ArticleOrderRules.checkBody(words(500) + "https://acme.example")).isEmpty();
    }

    @Test
    void aShortBodySaysHowManyWordsAreMissing() {
        assertThat(ArticleOrderRules.checkBody(words(180)))
                .containsExactly("body: Add at least 320 more words to meet the minimum content length");
    }

    @Test
    void aLinkWithoutADottedHostIsNotValid() {
        assertThat(ArticleOrderRules.checkBody(words(500) + "http://localhost/x"))
                .containsExactly("body: Check http://localhost/x - not a valid link");
    }

    @Test
    void moreThanThreeLinksIsAViolation() {
        String body = words(500) + "https://a.example https://b.example https://c.example https://d.example";

        assertThat(ArticleOrderRules.checkBody(body))
                .containsExactly("body: An article can carry at most 3 follow links");
    }
}
