package ro.prospero.aidir.service;

import org.junit.jupiter.api.Test;
import ro.prospero.aidir.data.ArticleBlock;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PublicationRulesTest {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-27T15:30:00Z");

    private static ArticleBlock paragraph(String text) {
        return new ArticleBlock.Paragraph(List.of(new ArticleBlock.Inline(text, null, false, false)));
    }

    @Test
    void aSlugIsLowerCaseAsciiWordsJoinedByHyphens() {
        assertThat(PublicationRules.slug("Acme Ships a Faster Writer!")).isEqualTo("acme-ships-a-faster-writer");
        assertThat(PublicationRules.slug("  GPT-5: what's new?  ")).isEqualTo("gpt-5-what-s-new");
    }

    @Test
    void accentsAreDroppedRatherThanTheLetters() {
        assertThat(PublicationRules.slug("Café déjà vu in São Paulo")).isEqualTo("cafe-deja-vu-in-sao-paulo");
    }

    @Test
    void aHeadlineWithNothingAUrlCanCarryFallsBack() {
        assertThat(PublicationRules.slug("人工智能")).isEqualTo(PublicationRules.FALLBACK_SLUG);
        assertThat(PublicationRules.slug("!!!")).isEqualTo(PublicationRules.FALLBACK_SLUG);
    }

    @Test
    void aLongHeadlineIsCutBetweenWords() {
        String slug = PublicationRules.slug("word ".repeat(40));

        assertThat(slug).hasSizeLessThanOrEqualTo(PublicationRules.MAX_SLUG_LENGTH)
                        .doesNotEndWith("-")
                        .matches("(word-)*word");
    }

    @Test
    void aLongSingleWordIsCutWhereverItReachesTheLimit() {
        assertThat(PublicationRules.slug("a".repeat(200))).hasSize(PublicationRules.MAX_SLUG_LENGTH);
    }

    @Test
    void aTakenSlugIsNumberedFromTwo() {
        assertThat(PublicationRules.uniqueSlug("acme", Set.of())).isEqualTo("acme");
        assertThat(PublicationRules.uniqueSlug("acme", Set.of("acme"))).isEqualTo("acme-2");
        assertThat(PublicationRules.uniqueSlug("acme", Set.of("acme", "acme-2", "acme-3"))).isEqualTo("acme-4");
    }

    @Test
    void aSlugTheFrontEndUsesForItsOwnPageIsNumbered() {
        assertThat(PublicationRules.uniqueSlug("pricing", Set.of())).isEqualTo("pricing-2");
    }

    @Test
    void theExcerptIsTheFirstParagraphWithText() {
        List<ArticleBlock> body = List.of(new ArticleBlock.Heading("Intro"),
                                          paragraph("   "),
                                          paragraph("The  first\nparagraph."),
                                          paragraph("The second."));

        assertThat(PublicationRules.excerpt(body)).isEqualTo("The first paragraph.");
    }

    @Test
    void aLongExcerptIsCutBetweenWords() {
        String excerpt = PublicationRules.excerpt(List.of(paragraph("word ".repeat(100))));

        assertThat(excerpt).endsWith("word…")
                           .hasSizeLessThanOrEqualTo(PublicationRules.MAX_EXCERPT_LENGTH + 1);
    }

    @Test
    void aBodyWithoutParagraphsHasAnEmptyExcerpt() {
        assertThat(PublicationRules.excerpt(List.of(new ArticleBlock.Heading("Only a heading")))).isEmpty();
    }

    @Test
    void readingTimeRoundsUpAndIsAtLeastAMinute() {
        assertThat(PublicationRules.readMinutes(List.of())).isEqualTo(1);
        assertThat(PublicationRules.readMinutes(List.of(paragraph("word ".repeat(200))))).isEqualTo(1);
        assertThat(PublicationRules.readMinutes(List.of(paragraph("word ".repeat(201))))).isEqualTo(2);
    }

    @Test
    void aBriefWithoutAPreferredDateOrWithOneGoneByGoesLiveNow() {
        assertThat(PublicationRules.briefPublishedAt(NOW, null, ZoneOffset.UTC)).isEqualTo(NOW);
        assertThat(PublicationRules.briefPublishedAt(NOW, LocalDate.of(2026, 10, 1), ZoneOffset.UTC)).isEqualTo(NOW);
        assertThat(PublicationRules.briefPublishedAt(NOW, LocalDate.of(2026, 10, 27), ZoneOffset.UTC)).isEqualTo(NOW);
    }

    @Test
    void aBriefWithAPreferredDateToComeGoesLiveAtTheStartOfThatDay() {
        assertThat(PublicationRules.briefPublishedAt(NOW, LocalDate.of(2026, 11, 3), ZoneOffset.UTC))
                .isEqualTo(OffsetDateTime.parse("2026-11-03T00:00:00Z"));
    }

    @Test
    void followLinksLastAYearUnlessPermanent() {
        assertThat(PublicationRules.followLinksUntil(NOW, true)).isNull();
        assertThat(PublicationRules.followLinksUntil(NOW, false)).isEqualTo(OffsetDateTime.parse("2027-10-27T15:30:00Z"));

        assertThat(PublicationRules.followLinks(null, NOW)).isTrue();
        assertThat(PublicationRules.followLinks(NOW.plusDays(1), NOW)).isTrue();
        assertThat(PublicationRules.followLinks(NOW, NOW)).isFalse();
    }
}
