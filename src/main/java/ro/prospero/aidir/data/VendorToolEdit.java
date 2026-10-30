package ro.prospero.aidir.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import static ro.prospero.aidir.data.VendorSubmissionPayload.NAME_MAX_LENGTH;
import static ro.prospero.aidir.data.VendorSubmissionPayload.SHORT_DESCRIPTION_MAX_LENGTH;

/**
 * What the AI Tool Profile page sends back, one record per card it edits. The limits are the onboarding
 * wizard's, so a listing cannot be edited into a shape the wizard would have refused. Every list replaces its
 * section whole.
 *
 * <p>The stored jsonb is read back as {@link ToolDetailsDTO.Feature} / {@link ToolDetailsDTO.Plan} and
 * {@link VendorToolDTO.Faq}; the records here carry the same fields, plus the validation those lack.
 */
public final class VendorToolEdit {
    public static final int URL_MAX_LENGTH = 250;
    public static final int LONG_DESCRIPTION_MAX_LENGTH = 1000;
    public static final int MAX_CATEGORIES = 3;
    public static final int MAX_TAGS = 5;
    public static final int MAX_FEATURES = 8;
    public static final int MAX_PLAN_FEATURES = 4;
    public static final int PLAN_FEATURE_MAX_LENGTH = 80;
    public static final int MAX_FAQS = 20;
    public static final int MAX_SCREENSHOTS = 5;

    private VendorToolEdit() {
    }

    public record General(
            @NotBlank @Size(max = NAME_MAX_LENGTH) String name,
            @NotBlank @Size(max = URL_MAX_LENGTH) String url,
            @NotBlank @Size(max = SHORT_DESCRIPTION_MAX_LENGTH) String shortDescription,
            @NotNull @Size(max = MAX_CATEGORIES) List<@NotBlank String> categories,
            @NotNull @Size(max = MAX_TAGS) List<@NotBlank String> tags
    ) {
    }

    public record Overview(@NotBlank @Size(max = LONG_DESCRIPTION_MAX_LENGTH) String longDescription) {
    }

    public record Feature(@NotBlank String id, @NotBlank String title, @NotBlank String description) {
    }

    public record Features(@NotNull @Size(max = MAX_FEATURES) @Valid List<Feature> features) {
    }

    public record Integrations(@NotNull List<@NotBlank String> integrations) {
    }

    public record Plan(
            @NotBlank String id,
            @NotBlank String name,
            String price,
            @NotBlank String billingType,
            @NotNull @Size(max = MAX_PLAN_FEATURES) List<@NotBlank @Size(max = PLAN_FEATURE_MAX_LENGTH) String> features
    ) {
    }

    public record Pricing(@NotNull @Valid List<Plan> plans, String freeTierPlanId, String highlightPlanId) {

        /** A flag pointing at a plan that is not in the list would mark nothing on the public page. */
        @JsonIgnore
        @AssertTrue(message = "freeTierPlanId and highlightPlanId must name plans in the list")
        public boolean isFlagsKnown() {
            if (plans == null) {
                return true;
            }
            HashSet<String> ids = new HashSet<>();
            plans.stream().map(Plan::id).filter(Objects::nonNull).forEach(ids::add);
            return (freeTierPlanId == null || ids.contains(freeTierPlanId))
                   && (highlightPlanId == null || ids.contains(highlightPlanId));
        }
    }

    public record Faq(@NotBlank String id, @NotBlank @Size(max = 300) String question,
                      @NotBlank @Size(max = 2000) String answer) {
    }

    public record Faqs(@NotNull @Size(max = MAX_FAQS) @Valid List<Faq> faqs) {
    }

    /**
     * The screenshots to keep, in the order to show them, by their {@link VendorToolDTO.Screenshot#id()}.
     * New screenshots arrive as files beside this and go after the kept ones.
     */
    public record Media(@Size(max = URL_MAX_LENGTH) String demoVideoUrl, @NotNull List<String> keepScreenshotIds) {
    }
}
