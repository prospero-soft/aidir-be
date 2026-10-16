package ro.prospero.aidir.data;

import jakarta.validation.constraints.AssertTrue;

/** The two checkboxes on the review step of both article flows. Neither order is taken without them. */
public record ArticleConsents(@AssertTrue(message = "Confirm that the information is accurate") boolean accurate,
                              @AssertTrue(message = "Accept the terms and the publication rules") boolean terms) {
}
