package ro.prospero.aidir.service;

import org.junit.jupiter.api.Test;
import ro.prospero.aidir.exception.ApiPayloadValidationException;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BriefDraftImagesTest {

    @Test
    void keptImagesComeFromTheBaseRoundAndNewOnesInTurn() {
        assertThat(ArticleBriefWritingService.keptImagePaths(Arrays.asList(1, null, 0, null),
                                                             List.of("a", "b"), List.of("new-1", "new-2")))
                .containsExactly("b", "new-1", "a", "new-2");
    }

    @Test
    void aDraftCanDropEveryImage() {
        assertThat(ArticleBriefWritingService.keptImagePaths(List.of(), List.of("a"), List.of())).isEmpty();
    }

    @Test
    void aPositionPastTheBaseRoundIsRefused() {
        assertThatThrownBy(() -> ArticleBriefWritingService.keptImagePaths(List.of(1), List.of("a"), List.of()))
                .isInstanceOf(ApiPayloadValidationException.class);
    }
}
