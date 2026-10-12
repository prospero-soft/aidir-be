package ro.prospero.aidir.data;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The history rows a paid signup sends. Their required fields match the NOT NULL columns, so an incomplete
 * row is a 400 naming the field rather than a 500 from the insert.
 */
class UserOnboardingPayloadValidationTest {
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void validator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void close() {
        factory.close();
    }

    private static List<String> violations(Object payload) {
        return validator.validate(payload)
                        .stream()
                        .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                        .sorted()
                        .toList();
    }

    private static final UserBasic BASIC =
            new UserBasic("Alex Morgan", "alex@aidir.test", "Germany", "password1", "password1", true);

    private static final UserOnboardingPayload.Education EDUCATION =
            new UserOnboardingPayload.Education("TUM", "Master's Degree", "2017", "Munich, Germany", "CS");

    private static UserOnboardingPayload payload(List<UserOnboardingPayload.Experience> experience,
                                                 List<UserOnboardingPayload.Certification> certifications,
                                                 List<UserOnboardingPayload.Course> courses,
                                                 List<UserOnboardingPayload.Project> projects,
                                                 List<UserOnboardingPayload.LanguageEntry> languages) {
        var profile = new UserOnboardingPayload.Profile(
                "AI Engineer",
                null,
                "Builds production AI systems.",
                new UserOnboardingPayload.WorkPreferences("Berlin, Germany", "Open within the EU", "Remote",
                                                          "Full-time"),
                List.of("Python", "RAG", "LLM"),
                null,
                experience,
                List.of(EDUCATION),
                certifications,
                courses,
                projects,
                languages);
        return new UserOnboardingPayload(BASIC, UserPlanKey.PRO, profile);
    }

    @Test
    void aSignupWithEveryOptionalSectionLeftOutPasses() {
        assertThat(violations(payload(null, null, null, null, null))).isEmpty();
    }

    @Test
    void aSignupWithCompleteRowsInEverySectionPasses() {
        var payload = payload(
                List.of(new UserOnboardingPayload.Experience("OpenAI", "AI Engineer", "", "", "", "")),
                List.of(new UserOnboardingPayload.Certification("AWS ML Specialty", "Amazon Web Services", "")),
                List.of(new UserOnboardingPayload.Course("Generative AI with LLMs", "", "", "")),
                List.of(new UserOnboardingPayload.Project("RAG Assistant", "", "", "")),
                List.of(new UserOnboardingPayload.LanguageEntry("English", "Fluent")));

        assertThat(violations(payload)).isEmpty();
    }

    /** The front end keeps a row that has either field filled in, so a company alone does reach the backend. */
    @Test
    void anExperienceRowWithACompanyAndNoPositionFails() {
        var payload = payload(List.of(new UserOnboardingPayload.Experience("OpenAI", "", "", "", "", "")),
                              null, null, null, null);

        assertThat(violations(payload)).containsExactly("profile.experience[0].position: must not be blank");
    }

    @Test
    void aCertificationWithoutItsOrganizationFails() {
        var payload = payload(null, List.of(new UserOnboardingPayload.Certification("AWS ML Specialty", " ", "2023")),
                              null, null, null);

        assertThat(violations(payload))
                .containsExactly("profile.certifications[0].organization: must not be blank");
    }

    @Test
    void aCourseWithoutItsNameFails() {
        var payload = payload(null, null, List.of(new UserOnboardingPayload.Course("", "DeepLearning.AI", "", "")),
                              null, null);

        assertThat(violations(payload)).containsExactly("profile.courses[0].name: must not be blank");
    }

    @Test
    void aProjectWithoutItsTitleFails() {
        var payload = payload(null, null, null, List.of(new UserOnboardingPayload.Project(null, "AI", "2024", "")),
                              null);

        assertThat(violations(payload)).containsExactly("profile.projects[0].title: must not be blank");
    }

    @Test
    void aLanguageWithoutItsNameFails() {
        var payload = payload(null, null, null, null,
                              List.of(new UserOnboardingPayload.LanguageEntry("", "Fluent")));

        assertThat(violations(payload)).containsExactly("profile.languages[0].language: must not be blank");
    }
}
