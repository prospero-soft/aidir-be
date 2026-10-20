package ro.prospero.aidir.service;

import org.junit.jupiter.api.Test;
import ro.prospero.aidir.data.ArticleBlock;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArticleBodyRulesTest {
    private static final ArticleBlock QUOTE = new ArticleBlock.PullQuote("A line worth repeating");

    private static ArticleBlock.Inline run(String text) {
        return new ArticleBlock.Inline(text, null, false, false);
    }

    private static ArticleBlock.Inline link(String href) {
        return new ArticleBlock.Inline(href, href, false, false);
    }

    private static ArticleBlock paragraph(ArticleBlock.Inline... runs) {
        return new ArticleBlock.Paragraph(List.of(runs));
    }

    private static ArticleBlock words(int n) {
        return paragraph(run("word ".repeat(n)));
    }

    private static ArticleBlock image(int n) {
        return new ArticleBlock.Image(n, "");
    }

    private static List<ArticleBlock> body(ArticleBlock... blocks) {
        return List.of(blocks);
    }

    @Test
    void wordsAreSplitOnAnyWhitespace() {
        assertThat(ArticleBodyRules.wordCount("  one\ttwo\n\nthree  ")).isEqualTo(3);
        assertThat(ArticleBodyRules.wordCount("   ")).isZero();
    }

    @Test
    void runsJoinWithinABlockAndBlocksStandApart() {
        ArticleBlock list = new ArticleBlock.ListBlock(false, List.of(List.of(run("four")), List.of(run("five"))));

        assertThat(ArticleBodyRules.wordCount(body(paragraph(run("o"), run("ne two")),
                                                    new ArticleBlock.Heading("three"), list))).isEqualTo(5);
    }

    @Test
    void pullQuotesAndAltTextAreNotCounted() {
        assertThat(ArticleBodyRules.wordCount(body(words(3), QUOTE, new ArticleBlock.Image(0, "a described image"))))
                .isEqualTo(3);
    }

    @Test
    void linksAreTheDistinctTargetsOfLinkRuns() {
        ArticleBlock list = new ArticleBlock.ListBlock(true, List.of(List.of(link("https://acme.example/b"))));

        assertThat(ArticleBodyRules.bodyLinks(body(paragraph(run("See "), link("https://acme.example/a")), list,
                                                    paragraph(link("https://acme.example/a")))))
                .containsExactly("https://acme.example/a", "https://acme.example/b");
    }

    @Test
    void theBilledLinksAreAtLeastOneAndAtMostThree() {
        assertThat(ArticleBodyRules.billedLinks(body(words(3)))).isEqualTo(1);
        assertThat(ArticleBodyRules.billedLinks(body(paragraph(link("https://a.example"), link("https://b.example")))))
                .isEqualTo(2);
        assertThat(ArticleBodyRules.billedLinks(body(paragraph(link("https://a.example"), link("https://b.example"),
                                                                link("https://c.example"), link("https://d.example")))))
                .isEqualTo(3);
    }

    @Test
    void aLongEnoughBodyWithFewLinksPasses() {
        assertThat(ArticleBodyRules.checkBody(body(words(500), paragraph(link("https://acme.example"))), 0)).isEmpty();
    }

    @Test
    void aShortBodySaysHowManyWordsAreMissing() {
        assertThat(ArticleBodyRules.checkBody(body(words(180)), 0))
                .containsExactly("body: Add at least 320 more words to meet the minimum content length");
    }

    @Test
    void aLinkWithoutADottedHostIsNotValid() {
        assertThat(ArticleBodyRules.checkBody(body(words(500), paragraph(link("http://localhost/x"))), 0))
                .containsExactly("body: Check http://localhost/x - not a valid link");
    }

    @Test
    void moreThanThreeLinksIsAViolation() {
        ArticleBlock links = paragraph(link("https://a.example"), link("https://b.example"),
                                       link("https://c.example"), link("https://d.example"));

        assertThat(ArticleBodyRules.checkBody(body(words(500), links), 0))
                .containsExactly("body: An article can carry at most 3 follow links");
    }

    @Test
    void aBodyOverTheCharacterLimitSaysHowMuchToCut() {
        // 4,001 five-character words: 20,005 characters.
        assertThat(ArticleBodyRules.checkBody(body(words(4001)), 0))
                .containsExactly("body: Cut 5 characters - an article can be at most 20000 characters long");
    }

    @Test
    void pullQuotesAndHeadingsCountTowardsTheCharacters() {
        ArticleBlock list = new ArticleBlock.ListBlock(false, List.of(List.of(run("abc"))));

        assertThat(ArticleBodyRules.characterCount(body(paragraph(run("ab"), run("cd")), new ArticleBlock.Heading("ef"),
                                                         QUOTE, list, new ArticleBlock.Image(0, "not counted"))))
                .isEqualTo(4 + 2 + "A line worth repeating".length() + 3);
    }

    @Test
    void theMostPullQuotesFollowTheLengthAndNoneAreRequired() {
        assertThat(ArticleBodyRules.checkBody(body(words(500), QUOTE, QUOTE), 0))
                .containsExactly("body: An article of 500 words carries at most 1 pull quote");
        assertThat(ArticleBodyRules.checkBody(body(words(2500)), 0)).isEmpty();
        assertThat(ArticleBodyRules.checkBody(body(words(1200), QUOTE, QUOTE), 0)).isEmpty();
    }

    @Test
    void theImagesAreNumberedInOrderOneForEachPart() {
        String mismatch = "body: The images in the article do not match the images sent with it";

        assertThat(ArticleBodyRules.checkBody(body(words(500), image(0), image(1)), 2)).isEmpty();
        assertThat(ArticleBodyRules.checkBody(body(words(500), image(0)), 2)).containsExactly(mismatch);
        assertThat(ArticleBodyRules.checkBody(body(words(500), image(1), image(0)), 2)).containsExactly(mismatch);
        assertThat(ArticleBodyRules.checkBody(body(words(500), image(0)), 0)).containsExactly(mismatch);
    }
}
