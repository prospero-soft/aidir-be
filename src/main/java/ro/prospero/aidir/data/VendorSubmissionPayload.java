package ro.prospero.aidir.data;


import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Mirrors OnboardingFormState in aidir-fe (src/components/vendor-onboarding/types.ts).
 * <p>
 * Purely visual state is not modelled and is dropped on the way in - the wizard's draft rows, accordion
 * indices and "add another" text fields have no place past the form.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VendorSubmissionPayload(
        @Valid @NotNull Company company,
        @NotNull PlanKey planKey,
        @Valid @NotNull Product product,
        @Valid @NotNull Account account
) {
    public static final int PASSWORD_MIN_LENGTH = 8;
    public static final int NAME_MAX_LENGTH = 200;
    public static final int SHORT_DESCRIPTION_MAX_LENGTH = 250;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Company(
            @NotBlank String officialName,
            @NotBlank String websiteUrl,
            @NotBlank @Email String workEmail,
            @NotBlank String description,
            List<String> selectedPromos
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Product(
            @NotBlank @Size(max = NAME_MAX_LENGTH) String name,
            @NotBlank @Size(max = SHORT_DESCRIPTION_MAX_LENGTH) String shortDescription,
            @NotBlank String longDescription,
            List<String> categories,
            List<FeatureEntry> features,
            String demoVideoUrl,
            List<PricingPlan> plans,
            String freeTierPlanId,
            String highlightPlanId,
            List<String> integrations,
            List<String> tags
            ) {
    }

    /**
     * The credentials half of the wizard's last step. There is no email here: the account is identified by
     * {@link Company#workEmail}, which is what step 6 shows read-only above the password fields.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Account(
            @NotBlank @Size(min = PASSWORD_MIN_LENGTH) String password,
            @NotBlank String confirmPassword,
            @AssertTrue(message = "account.agreed must be accepted") boolean agreed
    ) {
        @JsonIgnore
        @AssertTrue(message = "account.password and account.confirmPassword must match")
        public boolean isPasswordConfirmed() {
            return password != null && password.equals(confirmPassword);
        }
    }

    public record FeatureEntry(String id, String title, String description) {
    }

    public record PricingPlan(
            String id,
            String name,
            String price,
            String billingType,
            List<String> features
    ) {
    }
}
