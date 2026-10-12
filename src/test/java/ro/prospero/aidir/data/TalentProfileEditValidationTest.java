package ro.prospero.aidir.data;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** The rules each Resume / CV section has to pass before anything is written. */
class TalentProfileEditValidationTest {
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

    /** In the shape ApiPayloadValidationException reports them, so a failure reads like the 400 would. */
    private static List<String> violations(Object body) {
        return validator.validate(body)
                        .stream()
                        .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                        .sorted()
                        .toList();
    }

    private static String chars(int length) {
        return "x".repeat(length);
    }

    private static List<String> skills(int count) {
        return IntStream.range(0, count).mapToObj(i -> "Skill " + i).toList();
    }

    // General

    @Test
    void aCompleteGeneralSectionPasses() {
        assertThat(violations(new TalentProfileEdit.General("Alex Morgan", "Germany", "AI Engineer", "Builds agents")))
                .isEmpty();
    }

    @Test
    void theShortDescriptionIsOptional() {
        assertThat(violations(new TalentProfileEdit.General("Alex Morgan", "Germany", "AI Engineer", null))).isEmpty();
    }

    @Test
    void theNameCountryAndTitleAreRequired() {
        assertThat(violations(new TalentProfileEdit.General(" ", "", null, null)))
                .containsExactly("country: must not be blank",
                                 "fullName: must not be blank",
                                 "title: must not be blank");
    }

    @Test
    void theGeneralSectionKeepsToTheColumnLengths() {
        assertThat(violations(new TalentProfileEdit.General(chars(201), "Germany", chars(201), chars(181))))
                .containsExactly("fullName: size must be between 0 and 200",
                                 "shortDescription: size must be between 0 and 180",
                                 "title: size must be between 0 and 200");
    }

    @Test
    void theLongestNameTitleAndShortDescriptionPass() {
        assertThat(violations(new TalentProfileEdit.General(chars(200), "Germany", chars(200), chars(180)))).isEmpty();
    }

    // Summary

    @Test
    void aSummaryOfTheMaximumLengthPasses() {
        assertThat(violations(new TalentProfileEdit.Summary(chars(1000)))).isEmpty();
    }

    @Test
    void theSummaryIsRequired() {
        assertThat(violations(new TalentProfileEdit.Summary("  "))).containsExactly("summary: must not be blank");
    }

    @Test
    void theSummaryKeepsToItsColumnLength() {
        assertThat(violations(new TalentProfileEdit.Summary(chars(1001))))
                .containsExactly("summary: size must be between 0 and 1000");
    }

    // Skills

    @Test
    void threeToFifteenSkillsPass() {
        assertThat(violations(new TalentProfileEdit.Skills(skills(3)))).isEmpty();
        assertThat(violations(new TalentProfileEdit.Skills(skills(15)))).isEmpty();
    }

    @Test
    void fewerThanThreeOrMoreThanFifteenSkillsFail() {
        assertThat(violations(new TalentProfileEdit.Skills(skills(2))))
                .containsExactly("skills: size must be between 3 and 15");
        assertThat(violations(new TalentProfileEdit.Skills(skills(16))))
                .containsExactly("skills: size must be between 3 and 15");
    }

    @Test
    void theSkillListIsRequired() {
        assertThat(violations(new TalentProfileEdit.Skills(null))).containsExactly("skills: must not be null");
    }

    @Test
    void aSkillCannotBeListedTwice() {
        assertThat(violations(new TalentProfileEdit.Skills(List.of("Python", "RAG", "Python"))))
                .containsExactly("distinct: skills must not repeat");
    }

    // Languages

    @Test
    void noLanguagesIsAllowed() {
        assertThat(violations(new TalentProfileEdit.Languages(List.of()))).isEmpty();
    }

    @Test
    void aLanguageCannotBeListedTwice() {
        var languages = List.of(new UserOnboardingPayload.LanguageEntry("English", "Fluent"),
                                new UserOnboardingPayload.LanguageEntry("English", "Basic"));

        assertThat(violations(new TalentProfileEdit.Languages(languages)))
                .containsExactly("distinct: languages must not repeat");
    }

    @Test
    void aLanguageNeedsBothItsNameAndProficiency() {
        var languages = List.of(new UserOnboardingPayload.LanguageEntry("English", " "),
                                new UserOnboardingPayload.LanguageEntry(null, "Fluent"));

        assertThat(violations(new TalentProfileEdit.Languages(languages)))
                .containsExactly("languages[0].proficiency: must not be blank",
                                 "languages[1].language: must not be blank");
    }

    // Work experience

    @Test
    void noWorkExperienceIsAllowed() {
        assertThat(violations(new TalentProfileEdit.Experience(List.of()))).isEmpty();
    }

    @Test
    void anExperienceRowNeedsOnlyItsCompanyAndPosition() {
        var row = new UserOnboardingPayload.Experience("OpenAI", "AI Engineer", null, null, null, null);

        assertThat(violations(new TalentProfileEdit.Experience(List.of(row)))).isEmpty();
    }

    @Test
    void anExperienceRowWithoutCompanyOrPositionFails() {
        var row = new UserOnboardingPayload.Experience("", " ", "Berlin, Germany", "Remote", "2022 - 2026", "Agents");

        assertThat(violations(new TalentProfileEdit.Experience(List.of(row))))
                .containsExactly("experience[0].company: must not be blank",
                                 "experience[0].position: must not be blank");
    }

    @Test
    void theExperienceListIsRequired() {
        assertThat(violations(new TalentProfileEdit.Experience(null))).containsExactly("experience: must not be null");
    }

    // Education

    @Test
    void educationCannotBeEmptied() {
        assertThat(violations(new TalentProfileEdit.Education(List.of())))
                .containsExactly("education: must not be empty");
    }

    @Test
    void everyEducationFieldIsRequired() {
        var row = new UserOnboardingPayload.Education("", null, " ", "", null);

        assertThat(violations(new TalentProfileEdit.Education(List.of(row))))
                .containsExactly("education[0].degree: must not be blank",
                                 "education[0].field: must not be blank",
                                 "education[0].graduationYear: must not be blank",
                                 "education[0].institution: must not be blank",
                                 "education[0].location: must not be blank");
    }

    @Test
    void aCompleteEducationRowPasses() {
        var row = new UserOnboardingPayload.Education("TUM", "Master's Degree", "2017", "Munich, Germany", "CS");

        assertThat(violations(new TalentProfileEdit.Education(List.of(row)))).isEmpty();
    }

    // Certifications, courses, projects

    @Test
    void aCertificationNeedsItsNameAndOrganization() {
        var row = new UserOnboardingPayload.Certification(" ", "", "2023");

        assertThat(violations(new TalentProfileEdit.Certifications(List.of(row))))
                .containsExactly("certifications[0].name: must not be blank",
                                 "certifications[0].organization: must not be blank");
    }

    @Test
    void aCourseNeedsOnlyItsName() {
        assertThat(violations(new TalentProfileEdit.Courses(List.of(
                new UserOnboardingPayload.Course("Generative AI with LLMs", null, null, null)))))
                .isEmpty();
        assertThat(violations(new TalentProfileEdit.Courses(List.of(
                new UserOnboardingPayload.Course("", "DeepLearning.AI", "LLMs", "2024")))))
                .containsExactly("courses[0].name: must not be blank");
    }

    @Test
    void aProjectNeedsItsTitle() {
        var row = new UserOnboardingPayload.Project(null, "Generative AI", "2024", "A RAG assistant");

        assertThat(violations(new TalentProfileEdit.Projects(List.of(row))))
                .containsExactly("projects[0].title: must not be blank");
    }

    @Test
    void theOtherHistoryListsAreRequired() {
        List<String> all = new ArrayList<>();
        all.addAll(violations(new TalentProfileEdit.Certifications(null)));
        all.addAll(violations(new TalentProfileEdit.Courses(null)));
        all.addAll(violations(new TalentProfileEdit.Projects(null)));

        assertThat(all).containsExactly("certifications: must not be null",
                                        "courses: must not be null",
                                        "projects: must not be null");
    }

    // Work preferences, which the work route takes as onboarding's record

    @Test
    void everyWorkPreferenceIsRequired() {
        assertThat(violations(new UserOnboardingPayload.WorkPreferences("Berlin, Germany", "", " ", null)))
                .containsExactly("employmentType: must not be blank",
                                 "relocation: must not be blank",
                                 "workplace: must not be blank");
    }
}
