package ro.prospero.aidir.data;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Mirrors UserOnboardingFormState in aidir-fe (src/components/user-onboarding/types.ts).
 * <p>
 * Purely visual state is not modelled and is dropped on the way in (e.g. plan.openFaq, which tracks the
 * expanded FAQ accordion). Every list is expected to carry real rows only: the wizard seeds its sections
 * with blank placeholder rows, and those must be stripped before posting rather than sent as empty entries.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserOnboardingPayload(
        @Valid @NotNull Basic basic,
        @Valid @NotNull Plan plan,
        @Valid @NotNull Profile profile
) {
    public static final int MIN_SKILLS = 3;
    public static final int MAX_SKILLS = 15;
    public static final int SHORT_DESCRIPTION_MAX_LENGTH = 180;
    public static final int SUMMARY_MAX_LENGTH = 1000;

    public record Basic(
            @NotBlank String fullName,
            @NotBlank @Email String email,
            @NotBlank String country,
            @NotBlank String password,
            @NotBlank String confirmPassword,
            @AssertTrue(message = "basic.agreed must be accepted") boolean agreed
    ) {
        @JsonIgnore
        @AssertTrue(message = "basic.password and basic.confirmPassword must match")
        public boolean isPasswordConfirmed() {
            return password != null && password.equals(confirmPassword);
        }
    }

    public record Plan(
            String audience,
            @NotNull UserPlanKey selectedKey
    ) {
    }

    /**
     * Education is the only mandatory history section - entry-level talent has no work experience to list,
     * and certifications are not something every profession issues.
     */
    public record Profile(
            String resumeFileName,
            @NotBlank String title,
            @Size(max = SHORT_DESCRIPTION_MAX_LENGTH) String shortDescription,
            @NotBlank @Size(max = SUMMARY_MAX_LENGTH) String summary,
            @Valid @NotNull WorkPreferences work,
            @NotNull @Size(min = MIN_SKILLS, max = MAX_SKILLS) List<String> skills,
            @Valid ProfileLinks links,
            @Valid List<Experience> experience,
            @Valid @NotEmpty List<Education> education,
            @Valid List<Certification> certifications,
            @Valid List<Course> courses,
            @Valid List<Project> projects,
            @Valid List<LanguageEntry> languages
    ) {
    }

    public record WorkPreferences(
            @NotBlank String location,
            @NotBlank String relocation,
            @NotBlank String workplace,
            @NotBlank String employmentType
    ) {
    }

    public record ProfileLinks(String linkedin, String github, String portfolio, String other) {
    }

    // The ids below are the front-end's row keys ("exp-1", "edu-1", ...), not identifiers we own.
    public record Experience(
            String id,
            String company,
            String position,
            String location,
            String workplace,
            String period,
            String description
    ) {
    }

    public record Education(
            String id,
            @NotBlank String institution,
            @NotBlank String degree,
            @NotBlank String graduationYear,
            @NotBlank String location,
            @NotBlank String field
    ) {
    }

    public record Certification(String id, String name, String organization, String year) {
    }

    public record Course(String id, String name, String provider, String focus, String completionYear) {
    }

    public record Project(String id, String title, String category, String year, String description) {
    }

    public record LanguageEntry(String id, String language, String proficiency) {
    }
}
