package ro.prospero.aidir.service;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KeptImagesTest {

    @Test
    void storedImagesKeptOnceWithAPartForEachNewOnePass() {
        assertThat(ArticleOrderChecks.keptImageViolations(Arrays.asList(1, null, 0), 1)).isEmpty();
        assertThat(ArticleOrderChecks.keptImageViolations(List.of(), 0)).isEmpty();
    }

    @Test
    void aStoredImageKeptTwiceIsRefused() {
        assertThat(ArticleOrderChecks.keptImageViolations(List.of(0, 0), 0))
                .containsExactly("keptImages: Keep each stored image at most once");
    }

    @Test
    void aNegativePositionIsRefused() {
        assertThat(ArticleOrderChecks.keptImageViolations(List.of(-1), 0))
                .containsExactly("keptImages: Keep each stored image at most once");
    }

    @Test
    void newImagesNeedAPartEachAndNoMore() {
        assertThat(ArticleOrderChecks.keptImageViolations(Arrays.asList(null, null), 1))
                .containsExactly("additionalImages: Send a file for each new image, 2 in all");
        assertThat(ArticleOrderChecks.keptImageViolations(List.of(0), 1))
                .containsExactly("additionalImages: Send a file for each new image, 0 in all");
    }
}
