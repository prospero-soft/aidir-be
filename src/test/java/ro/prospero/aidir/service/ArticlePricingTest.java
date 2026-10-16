package ro.prospero.aidir.service;

import org.junit.jupiter.api.Test;
import ro.prospero.aidir.data.ArticleQuoteDTO;
import ro.prospero.aidir.data.ArticleQuoteDTO.Line;
import ro.prospero.aidir.data.WritingTier;

import static org.assertj.core.api.Assertions.assertThat;

/** The totals here are the ones quote() in aidir-fe's src/data/newsPricing.ts gives for the same orders. */
class ArticlePricingTest {

    @Test
    void theBaseArticleIsTheOnlyLine() {
        ArticleQuoteDTO quote = ArticlePricing.quote(1, null, false, false, 0);

        assertThat(quote.lines()).containsExactly(new Line("Sponsored article - 1 follow EML link", 79));
        assertThat(quote.total()).isEqualTo(79);
    }

    @Test
    void aSubmittedArticleWithEverythingAdded() {
        ArticleQuoteDTO quote = ArticlePricing.quote(3, null, true, true, 2);

        assertThat(quote.lines()).containsExactly(new Line("Sponsored article - 1 follow EML link", 79),
                                                  new Line("Additional follow EML links ×2", 46),
                                                  new Line("Permanent follow links", 45),
                                                  new Line("Publish without sponsored label", 12),
                                                  new Line("Additional images ×2", 20));
        assertThat(quote.subtotal()).isEqualTo(202);
        assertThat(quote.discount()).isZero();
        assertThat(quote.total()).isEqualTo(202);
    }

    @Test
    void theWritingTierComesStraightAfterTheLinks() {
        ArticleQuoteDTO quote = ArticlePricing.quote(2, WritingTier.STANDARD, true, false, 0);

        assertThat(quote.lines()).extracting(Line::label)
                                 .containsExactly("Sponsored article - 1 follow EML link",
                                                  "Additional follow EML links ×1",
                                                  "Standard article writing (800–1,200 words)",
                                                  "Permanent follow links");
        assertThat(quote.total()).isEqualTo(79 + 23 + 149 + 45);
    }

    @Test
    void theDiscountIsTheHighestTierReachedRoundedHalfUp() {
        assertThat(ArticlePricing.discountFor(1249)).isZero();
        assertThat(ArticlePricing.discountFor(1255)).isEqualTo(126);
        assertThat(ArticlePricing.discountFor(2500)).isEqualTo(375);
        assertThat(ArticlePricing.discountFor(4000)).isEqualTo(800);
    }
}
