package ro.prospero.aidir.endpoint;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The {@code dev} filter chain: everything open, CSRF off, and signing in still a real thing. */
@ActiveProfiles("dev")
class DevProfileSecurityTest extends EndpointSecurityTestBase {

    @Test
    void anAdminEndpointAnswersAnyone() throws Exception {
        mockMvc.perform(get("/api/submission/queue"))
               .andExpect(status().isOk());
    }

    @Test
    void aPostWithoutTheCsrfHeaderIsNotRejected() throws Exception {
        mockMvc.perform(post("/api/search/reindex"))
               .andExpect(status().isOk());

        verify(documentIndexService).rebuildAll();
    }

    @Test
    void meIsStillA401WithoutASession() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
               .andExpect(status().isUnauthorized())
               .andExpect(jsonPath("$.detail").value("You need to sign in to do that."));
    }

    /** Nothing is enforced under dev, so it is the handler's own check that stops this. */
    @Test
    void aProfileEditIsStillA401WithoutASession() throws Exception {
        mockMvc.perform(put("/api/talent/me/summary").contentType(MediaType.APPLICATION_JSON)
                                                     .content("{\"summary\":\"Builds production AI systems.\"}"))
               .andExpect(status().isUnauthorized())
               .andExpect(jsonPath("$.detail").value("You need to sign in to do that."));

        verifyNoInteractions(talentProfileEditService);
    }

    /** The vendor routes check the account type themselves for the same reason. */
    @Test
    void vendorDealsStillNeedAVendor() throws Exception {
        mockMvc.perform(get("/api/vendor/deals"))
               .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/vendor/deals").cookie(signIn(TALENT)))
               .andExpect(status().isForbidden());

        verifyNoInteractions(dealService);
    }

    @Test
    void signingInStillCreatesASessionAndItsCookieWorksOverPlainHttp() throws Exception {
        MvcResult result = mockMvc.perform(login(post("/api/auth/login"), VENDOR, PASSWORD, false))
                                  .andExpect(status().isOk())
                                  .andExpect(cookie().secure(SESSION_COOKIE, false))
                                  .andReturn();
        Cookie session = sessionCookie(result);

        mockMvc.perform(get("/api/auth/me").cookie(session))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.email").value(VENDOR));
    }
}
