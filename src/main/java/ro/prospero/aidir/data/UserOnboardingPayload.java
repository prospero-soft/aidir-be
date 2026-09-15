package ro.prospero.aidir.data;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A talent signup on one of the paid plans, which is the only kind that builds a profile.
 * <p>
 * Nothing purely visual is modelled: the wizard's audience toggle, its expanded FAQ and the row keys
 * its list sections carry ("exp-1", "edu-1") stay on the front end, along with the uploaded CV, which
 * is there to fill the form in rather than to be stored. Every list is expected to carry real rows
 * only - the wizard seeds its sections with blank placeholders, and those are stripped before posting
 * rather than sent as empty entries.
 */
public record UserOnboardingPayload(
        @Valid @NotNull UserBasic basic,
        @NotNull UserPlanKey planKey,
        @Valid @NotNull Profile profile
) {
    public static final int MIN_SKILLS = 3;
    public static final int MAX_SKILLS = 15;
    public static final int SHORT_DESCRIPTION_MAX_LENGTH = 180;
    public static final int SUMMARY_MAX_LENGTH = 1000;

    @JsonIgnore
    @AssertTrue(message = "the free plan has no profile; use api/user-onboarding/free")
    public boolean isPaidPlan() {
        return planKey != UserPlanKey.FREE;
    }

    /**
     * Education is the only mandatory history section - entry-level talent has no work experience to list,
     * and certifications are not something every profession issues.
     */
    public record Profile(
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

    public record Experience(
            String company,
            String position,
            String location,
            String workplace,
            String period,
            String description
    ) {
    }

    public record Education(
            @NotBlank String institution,
            @NotBlank String degree,
            @NotBlank String graduationYear,
            @NotBlank String location,
            @NotBlank String field
    ) {
    }

    public record Certification(String name, String organization, String year) {
    }

    public record Course(String name, String provider, String focus, String completionYear) {
    }

    public record Project(String title, String category, String year, String description) {
    }

    public record LanguageEntry(String language, String proficiency) {
    }
}
