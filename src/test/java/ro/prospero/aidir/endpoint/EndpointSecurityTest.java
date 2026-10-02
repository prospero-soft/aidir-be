package ro.prospero.aidir.endpoint;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The enforced filter chain: what runs when the {@code dev} profile is not active. */
class EndpointSecurityTest extends EndpointSecurityTestBase {

    @Test
    void aWrongPasswordIsA401AndStartsNoSession() throws Exception {
        mockMvc.perform(login(postWithCsrf("/api/auth/login"), VENDOR, "not-the-password", false))
               .andExpect(status().isUnauthorized())
               .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
               .andExpect(jsonPath("$.detail").value("Incorrect email or password."))
               .andExpect(cookie().doesNotExist(SESSION_COOKIE));
    }

    @Test
    void anUnknownEmailAnswersExactlyAsAWrongPasswordDoes() throws Exception {
        mockMvc.perform(login(postWithCsrf("/api/auth/login"), "nobody@aidir.test", PASSWORD, false))
               .andExpect(status().isUnauthorized())
               .andExpect(jsonPath("$.detail").value("Incorrect email or password."))
               .andExpect(cookie().doesNotExist(SESSION_COOKIE));
    }

    @Test
    void aLoginWithNoPasswordIsA400() throws Exception {
        mockMvc.perform(login(postWithCsrf("/api/auth/login"), VENDOR, "", false))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.constraintViolations[0]").value("password: must not be blank"));
    }

    @Test
    void aCorrectLoginReturnsThePrincipalAndASessionCookie() throws Exception {
        MvcResult result = mockMvc.perform(login(postWithCsrf("/api/auth/login"), VENDOR, PASSWORD, false))
                                  .andExpect(status().isOk())
                                  .andExpect(jsonPath("$.accountId").value(1))
                                  .andExpect(jsonPath("$.email").value(VENDOR))
                                  .andExpect(jsonPath("$.name").value("Acme Tools"))
                                  .andExpect(jsonPath("$.accountType").value("vendor"))
                                  .andExpect(jsonPath("$.planKey").value("premium"))
                                  .andExpect(cookie().httpOnly(SESSION_COOKIE, true))
                                  .andExpect(cookie().secure(SESSION_COOKIE, true))
                                  .andExpect(cookie().sameSite(SESSION_COOKIE, "Lax"))
                                  // No Max-Age: the browser drops the cookie when it closes.
                                  .andExpect(cookie().maxAge(SESSION_COOKIE, -1))
                                  .andReturn();

        assertThat(sessionOf(sessionCookie(result)).getMaxInactiveInterval()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void rememberMeMakesTheCookiePersistentAndTheSessionLastThirtyDays() throws Exception {
        MvcResult result = mockMvc.perform(login(postWithCsrf("/api/auth/login"), VENDOR, PASSWORD, true))
                                  .andExpect(status().isOk())
                                  .andReturn();

        assertThat(result.getResponse().getCookie(SESSION_COOKIE).getMaxAge()).isPositive();
        assertThat(sessionOf(sessionCookie(result)).getMaxInactiveInterval()).isEqualTo(Duration.ofDays(30));
    }

    @Test
    void theSessionHoldsNoPasswordHash() throws Exception {
        Cookie session = signIn(VENDOR);

        Object context = sessionOf(session).getAttribute("SPRING_SECURITY_CONTEXT");

        assertThat(context).extracting("authentication.principal.password").isNull();
        assertThat(context).extracting("authentication.credentials").isNull();
    }

    /**
     * The token the caller signed in with is cleared, and a POST straight after signing in has to
     * carry the new one - so the new one must be on the login response, after the clearing.
     */
    @Test
    void aLoginReplacesTheCsrfToken() throws Exception {
        MvcResult result = mockMvc.perform(login(postWithCsrf("/api/auth/login"), VENDOR, PASSWORD, false))
                                  .andExpect(status().isOk())
                                  .andReturn();

        List<Cookie> csrfCookies = Arrays.stream(result.getResponse().getCookies())
                                         .filter(cookie -> CSRF_COOKIE.equals(cookie.getName()))
                                         .toList();
        Cookie last = csrfCookies.get(csrfCookies.size() - 1);

        assertThat(last.getValue()).isNotBlank().isNotEqualTo(CSRF_TOKEN);
        assertThat(last.isHttpOnly()).isFalse();
        assertThat(last.getSecure()).isTrue();
    }

    @Test
    void meIsA401WithoutASessionAndStillHandsOutACsrfToken() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/me"))
                                  .andExpect(status().isUnauthorized())
                                  .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                                  .andExpect(jsonPath("$.detail").value("You need to sign in to do that."))
                                  .andExpect(cookie().doesNotExist(SESSION_COOKIE))
                                  .andReturn();

        assertThat(result.getResponse().getCookie(CSRF_COOKIE).getValue()).isNotBlank();
    }

    @Test
    void meReturnsThePrincipalOfTheSession() throws Exception {
        Cookie session = signIn(ADMIN);

        mockMvc.perform(get("/api/auth/me").cookie(session))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.email").value(ADMIN))
               .andExpect(jsonPath("$.accountType").value("admin"))
               .andExpect(jsonPath("$.planKey").value(nullValue()));
    }

    @Test
    void loggingOutEndsTheSession() throws Exception {
        Cookie session = signIn(VENDOR);

        mockMvc.perform(postWithCsrf("/api/auth/logout").cookie(session))
               .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").cookie(session))
               .andExpect(status().isUnauthorized());
    }

    @Test
    void theSubmissionQueueIsTheAdminsOnly() throws Exception {
        mockMvc.perform(get("/api/submission/queue"))
               .andExpect(status().isUnauthorized())
               .andExpect(jsonPath("$.detail").value("You need to sign in to do that."));

        mockMvc.perform(get("/api/submission/queue").cookie(signIn(VENDOR)))
               .andExpect(status().isForbidden())
               .andExpect(jsonPath("$.detail").value("Your account is not allowed to do that."));

        mockMvc.perform(get("/api/submission/queue").cookie(signIn(ADMIN)))
               .andExpect(status().isOk());
    }

    @Test
    void aPostWithoutTheCsrfHeaderIsA403() throws Exception {
        mockMvc.perform(login(post("/api/auth/login"), VENDOR, PASSWORD, false))
               .andExpect(status().isForbidden())
               .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
               .andExpect(jsonPath("$.detail").value("The CSRF token is missing or does not match."));
    }

    @Test
    void aSessionAloneDoesNotGetAPostThrough() throws Exception {
        mockMvc.perform(post("/api/search/reindex").cookie(signIn(ADMIN)))
               .andExpect(status().isForbidden());

        verify(documentIndexService, never()).rebuildAll();
    }

    @Test
    void anAdminCanReindexAndApproveTheQueueWithAPost() throws Exception {
        Cookie admin = signIn(ADMIN);

        mockMvc.perform(postWithCsrf("/api/search/reindex").cookie(admin))
               .andExpect(status().isOk());
        mockMvc.perform(postWithCsrf("/api/submission/devApproveAll").cookie(admin))
               .andExpect(status().isOk());

        verify(documentIndexService).rebuildAll();
    }

    @Test
    void reindexAndApproveAllNoLongerAnswerAGet() throws Exception {
        Cookie admin = signIn(ADMIN);

        mockMvc.perform(get("/api/search/reindex").cookie(admin))
               .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(get("/api/submission/devApproveAll").cookie(admin))
               .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void thePublicGetsNeedNoSession() throws Exception {
        mockMvc.perform(get("/api/category")).andExpect(status().isOk());
        mockMvc.perform(get("/api/search").param("query", "widget")).andExpect(status().isOk());
        mockMvc.perform(get("/api/search/preview").param("query", "widget")).andExpect(status().isOk());
    }

    @Test
    void aPathWithNoRuleNeedsASession() throws Exception {
        mockMvc.perform(get("/api/not-an-endpoint"))
               .andExpect(status().isUnauthorized());
    }
}
