package ro.prospero.aidir.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.HashSet;
import java.util.List;

import static ro.prospero.aidir.data.UserOnboardingPayload.MAX_SKILLS;
import static ro.prospero.aidir.data.UserOnboardingPayload.MIN_SKILLS;
import static ro.prospero.aidir.data.UserOnboardingPayload.SHORT_DESCRIPTION_MAX_LENGTH;
import static ro.prospero.aidir.data.UserOnboardingPayload.SUMMARY_MAX_LENGTH;

/**
 * What the Resume / CV page sends back, one record per section it edits. The rules are the onboarding
 * wizard's, so a profile cannot be edited into a shape signup would have refused; the work preferences and
 * links reuse {@link UserOnboardingPayload}'s records outright.
 *
 * <p>Every list replaces its section whole - see {@code TalentProfileSections}.
 */
public final class TalentProfileEdit {

    private TalentProfileEdit() {
    }

    /** The name and country are the account's; they live on talent_profile because the directory reads them there. */
    public record General(
            @NotBlank @Size(max = UserBasic.FULL_NAME_MAX_LENGTH) String fullName,
            @NotBlank String country,
            @NotBlank @Size(max = 200) String title,
            @Size(max = SHORT_DESCRIPTION_MAX_LENGTH) String shortDescription
    ) {
    }

    public record Summary(@NotBlank @Size(max = SUMMARY_MAX_LENGTH) String summary) {
    }

    public record Skills(@NotNull @Size(min = MIN_SKILLS, max = MAX_SKILLS) List<String> skills) {

        /** talent_skill is unique per account, so a repeat would fail as a conflict rather than a bad request. */
        @JsonIgnore
        @AssertTrue(message = "skills must not repeat")
        public boolean isDistinct() {
            return skills == null || new HashSet<>(skills).size() == skills.size();
        }
    }

    public record Languages(@NotNull @Valid List<UserOnboardingPayload.LanguageEntry> languages) {

        @JsonIgnore
        @AssertTrue(message = "languages must not repeat")
        public boolean isDistinct() {
            return languages == null
                   || languages.stream().map(UserOnboardingPayload.LanguageEntry::language).distinct().count()
                      == languages.size();
        }
    }

    public record Experience(@NotNull @Valid List<UserOnboardingPayload.Experience> experience) {
    }

    /** Education is the one history section signup requires, so it cannot be emptied afterwards either. */
    public record Education(@NotEmpty @Valid List<UserOnboardingPayload.Education> education) {
    }

    public record Certifications(@NotNull @Valid List<UserOnboardingPayload.Certification> certifications) {
    }

    public record Courses(@NotNull @Valid List<UserOnboardingPayload.Course> courses) {
    }

    public record Projects(@NotNull @Valid List<UserOnboardingPayload.Project> projects) {
    }
}
