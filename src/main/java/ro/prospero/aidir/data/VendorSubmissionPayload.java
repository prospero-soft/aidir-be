package ro.prospero.aidir.data;


import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VendorSubmissionPayload(
        @Valid @NotNull Company company,
        @NotNull PlanKey planKey,
        @Valid @NotNull Product product
) {
    public record Company(
            @NotBlank String officialName,
            @NotBlank String websiteUrl,
            @NotBlank String workEmail,
            @NotBlank String description,
            List<String> selectedPromos
            //TODO: add to model, this at the company account level (so should be moved out of product)
    ) {
    }

    public record Product(
            @NotBlank String name,
            @NotBlank String shortDescription,
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
