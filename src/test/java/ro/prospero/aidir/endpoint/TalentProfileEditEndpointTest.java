package ro.prospero.aidir.endpoint;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.server.ResponseStatusException;
import ro.prospero.aidir.data.TalentCardDTO;
import ro.prospero.aidir.data.TalentDetailsDTO;
import ro.prospero.aidir.data.TalentProfileEdit;
import ro.prospero.aidir.data.UserOnboardingPayload;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Resume / CV routes as the web layer sees them: who may call them, whose profile they reach, and what
 * a bad body answers. The service is a stand-in - what the writes do to the database is not tested here.
 */
class TalentProfileEditEndpointTest extends EndpointSecurityTestBase {

    private static final String SUMMARY = "{\"summary\":\"Builds production AI systems.\"}";

    private ResultActions edit(Cookie session, String section, String json) throws Exception {
        return mockMvc.perform(putWithCsrf("/api/talent/me/" + section).cookie(session)
                                                                     .contentType(MediaType.APPLICATION_JSON)
                                                                     .content(json));
    }

    private static TalentDetailsDTO details() {
        var card = new TalentCardDTO(TALENT_ID, "Alex Morgan", "AI Engineer", null, "Builds production AI systems.",
                                     "Germany", "Berlin, Germany", "Open within the EU", "Remote", "Full-time",
                                     List.of("Python", "RAG", "LLM"), List.of("English"),
                                     OffsetDateTime.parse("2026-02-01T10:00:00Z"));
        return new TalentDetailsDTO(card, new TalentDetailsDTO.Links(null, null, null, null), List.of(), List.of(),
                                    List.of(), List.of(), List.of(), List.of(), List.of());
    }

    @Test
    void anEditWithoutASessionIsA401() throws Exception {
        mockMvc.perform(putWithCsrf("/api/talent/me/summary").contentType(MediaType.APPLICATION_JSON)
                                                             .content(SUMMARY))
               .andExpect(status().isUnauthorized())
               .andExpect(jsonPath("$.detail").value("You need to sign in to do that."));

        verifyNoInteractions(talentProfileEditService);
    }

    @Test
    void anEditWithoutTheCsrfHeaderIsA403() throws Exception {
        mockMvc.perform(put("/api/talent/me/summary").cookie(signIn(TALENT))
                                                     .contentType(MediaType.APPLICATION_JSON)
                                                     .content(SUMMARY))
               .andExpect(status().isForbidden())
               .andExpect(jsonPath("$.detail").value("The CSRF token is missing or does not match."));

        verifyNoInteractions(talentProfileEditService);
    }

    @Test
    void anEditAnswersWithTheProfileAsItNowReads() throws Exception {
        when(talentProfileEditService.updateSummary(anyLong(), any())).thenReturn(details());

        edit(signIn(TALENT), "summary", SUMMARY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.card.id").value(TALENT_ID))
                .andExpect(jsonPath("$.card.summary").value("Builds production AI systems."));
    }

    /** No route takes an id: the profile edited is always the session's own. */
    @Test
    void everySectionReachesItsWriteForTheSessionsAccount() throws Exception {
        Cookie session = signIn(TALENT);

        edit(session, "general", """
                {"fullName":"Alex Morgan","country":"Germany","title":"AI Engineer","shortDescription":"Agents"}
                """).andExpect(status().isOk());
        edit(session, "summary", SUMMARY).andExpect(status().isOk());
        edit(session, "work", """
                {"location":"Berlin, Germany","relocation":"Open within the EU","workplace":"Remote",
                 "employmentType":"Full-time"}
                """).andExpect(status().isOk());
        edit(session, "links", """
                {"linkedin":"https://linkedin.com/in/alex","github":"","portfolio":"","other":""}
                """).andExpect(status().isOk());
        edit(session, "skills", """
                {"skills":["Python","RAG","LLM"]}
                """).andExpect(status().isOk());
        edit(session, "languages", """
                {"languages":[{"language":"English","proficiency":"Fluent"}]}
                """).andExpect(status().isOk());
        edit(session, "experience", """
                {"experience":[{"company":"OpenAI","position":"AI Engineer","location":"","workplace":"Remote",
                                "period":"2022 - Present","description":""}]}
                """).andExpect(status().isOk());
        edit(session, "education", """
                {"education":[{"institution":"TUM","degree":"Master's Degree","graduationYear":"2017",
                               "location":"Munich, Germany","field":"CS"}]}
                """).andExpect(status().isOk());
        edit(session, "certifications", """
                {"certifications":[{"name":"AWS ML Specialty","organization":"Amazon Web Services","year":"2023"}]}
                """).andExpect(status().isOk());
        edit(session, "courses", """
                {"courses":[{"name":"Generative AI with LLMs","provider":"","focus":"","completionYear":""}]}
                """).andExpect(status().isOk());
        edit(session, "projects", """
                {"projects":[{"title":"RAG Assistant","category":"","year":"","description":""}]}
                """).andExpect(status().isOk());

        verify(talentProfileEditService).updateGeneral(
                eq(TALENT_ID), eq(new TalentProfileEdit.General("Alex Morgan", "Germany", "AI Engineer", "Agents")));
        verify(talentProfileEditService).updateSummary(
                eq(TALENT_ID), eq(new TalentProfileEdit.Summary("Builds production AI systems.")));
        verify(talentProfileEditService).updateWork(
                eq(TALENT_ID), eq(new UserOnboardingPayload.WorkPreferences("Berlin, Germany", "Open within the EU",
                                                                           "Remote", "Full-time")));
        verify(talentProfileEditService).updateLinks(
                eq(TALENT_ID), eq(new UserOnboardingPayload.ProfileLinks("https://linkedin.com/in/alex", "", "", "")));
        verify(talentProfileEditService).replaceSkills(
                eq(TALENT_ID), eq(new TalentProfileEdit.Skills(List.of("Python", "RAG", "LLM"))));
        verify(talentProfileEditService).replaceLanguages(
                eq(TALENT_ID), eq(new TalentProfileEdit.Languages(List.of(
                        new UserOnboardingPayload.LanguageEntry("English", "Fluent")))));
        verify(talentProfileEditService).replaceExperience(
                eq(TALENT_ID), eq(new TalentProfileEdit.Experience(List.of(
                        new UserOnboardingPayload.Experience("OpenAI", "AI Engineer", "", "Remote", "2022 - Present",
                                                             "")))));
        verify(talentProfileEditService).replaceEducation(
                eq(TALENT_ID), eq(new TalentProfileEdit.Education(List.of(
                        new UserOnboardingPayload.Education("TUM", "Master's Degree", "2017", "Munich, Germany",
                                                            "CS")))));
        verify(talentProfileEditService).replaceCertifications(
                eq(TALENT_ID), eq(new TalentProfileEdit.Certifications(List.of(
                        new UserOnboardingPayload.Certification("AWS ML Specialty", "Amazon Web Services",
                                                                "2023")))));
        verify(talentProfileEditService).replaceCourses(
                eq(TALENT_ID), eq(new TalentProfileEdit.Courses(List.of(
                        new UserOnboardingPayload.Course("Generative AI with LLMs", "", "", "")))));
        verify(talentProfileEditService).replaceProjects(
                eq(TALENT_ID), eq(new TalentProfileEdit.Projects(List.of(
                        new UserOnboardingPayload.Project("RAG Assistant", "", "", "")))));
    }

    @Test
    void anInvalidBodyIsA400NamingTheRuleAndWritesNothing() throws Exception {
        Cookie session = signIn(TALENT);

        edit(session, "skills", """
                {"skills":["Python","RAG","Python"]}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Validating the profile edit failed."))
                .andExpect(jsonPath("$.constraintViolations[0]").value("distinct: skills must not repeat"));

        edit(session, "education", """
                {"education":[]}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.constraintViolations[0]").value("education: must not be empty"));

        verifyNoInteractions(talentProfileEditService);
    }

    @Test
    void anUnreadableBodyIsA400() throws Exception {
        edit(signIn(TALENT), "summary", "{\"summary\":")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(talentProfileEditService);
    }

    /** A vendor, an admin or a free-tier talent is signed in with no profile to edit. */
    @Test
    void anAccountWithNoTalentProfileIsA404() throws Exception {
        when(talentProfileEditService.updateSummary(anyLong(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Account 1 has no talent profile"));

        edit(signIn(VENDOR), "summary", SUMMARY)
                .andExpect(status().isNotFound());

        verify(talentProfileEditService).updateSummary(eq(1L), any());
    }
}
