package ro.prospero.aidir.endpoint;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.session.SessionAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.session.MapSession;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ro.prospero.aidir.config.SecurityConfig;
import ro.prospero.aidir.config.StorageConfig;
import ro.prospero.aidir.data.PrincipalDTO;
import ro.prospero.aidir.event.MetricEventPublisher;
import ro.prospero.aidir.jooq.generated.public_.tables.records.AccountRecord;
import ro.prospero.aidir.security.AccountUserDetailsService;
import ro.prospero.aidir.service.AccountService;
import ro.prospero.aidir.service.DocumentIndexService;
import ro.prospero.aidir.service.DocumentSearchService;
import ro.prospero.aidir.service.ToolBrowseService;
import ro.prospero.aidir.service.ToolSubmissionService;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The web layer with the real security configuration and no database: the services are mocks, and
 * three accounts exist only as far as {@link AccountService} says they do.
 *
 * <p>Sessions go through Spring Session, as they do in the running application, but into a map rather
 * than Postgres. That is what lets these tests look at the session cookie the browser would be sent -
 * MockMvc's own session never produces one. The cookie itself is configured by the same
 * auto-configuration as in production, from the same properties.
 */
@WebMvcTest(controllers = {AuthEndpoint.class, ToolSubmissionEndpoint.class, SearchEndpoint.class,
                           CategoryEndpoint.class},
            properties = "aidir.uploads.root=target/test-uploads")
@Import({SecurityConfig.class, StorageConfig.class, AccountUserDetailsService.class,
         EndpointSecurityTestBase.InMemorySessions.class})
@ImportAutoConfiguration(SessionAutoConfiguration.class)
abstract class EndpointSecurityTestBase {
    static final String SESSION_COOKIE = "SESSION";
    static final String CSRF_COOKIE = "XSRF-TOKEN";
    static final String CSRF_HEADER = "X-XSRF-TOKEN";
    static final String CSRF_TOKEN = "a-token-the-front-end-read-from-its-cookie";

    static final String PASSWORD = "password";
    static final String VENDOR = "vendor@aidir.test";
    static final String ADMIN = "admin@aidir.test";

    // The lowest cost bcrypt allows: these tests sign in many times and are not testing the hash.
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(4).encode(PASSWORD);

    @Autowired
    MockMvc mockMvc;
    @Autowired
    MapSessionRepository sessionRepository;

    @MockitoBean
    AccountService accountService;
    @MockitoBean
    ToolSubmissionService toolSubmissionService;
    @MockitoBean
    ToolBrowseService toolBrowseService;
    @MockitoBean
    DocumentSearchService documentSearchService;
    @MockitoBean
    DocumentIndexService documentIndexService;
    @MockitoBean
    MetricEventPublisher metricEventPublisher;

    @TestConfiguration(proxyBeanMethods = false)
    @EnableSpringHttpSession
    static class InMemorySessions {
        @Bean
        MapSessionRepository sessionRepository() {
            return new MapSessionRepository(new ConcurrentHashMap<>());
        }
    }

    @BeforeEach
    void accounts() {
        account(1L, VENDOR, "vendor", "Acme Tools", "premium");
        account(2L, ADMIN, "admin", "Administrator", null);
    }

    private void account(long id, String email, String accountType, String name, String planKey) {
        AccountRecord record = new AccountRecord();
        record.setId(id);
        record.setEmail(email);
        record.setAccountType(accountType);
        record.setPassword(PASSWORD_HASH);

        when(accountService.findByEmail(email)).thenReturn(Optional.of(record));
        when(accountService.principalOf(id))
                .thenReturn(Optional.of(new PrincipalDTO(id, email, name, accountType, planKey, null)));
    }

    /** A POST the way the front end sends one: the token from the cookie, repeated in the header. */
    static MockHttpServletRequestBuilder postWithCsrf(String url) {
        return post(url).cookie(new Cookie(CSRF_COOKIE, CSRF_TOKEN)).header(CSRF_HEADER, CSRF_TOKEN);
    }

    static MockHttpServletRequestBuilder login(MockHttpServletRequestBuilder request, String email,
                                               String password, boolean remember) {
        return request.contentType(MediaType.APPLICATION_JSON)
                      .content("{\"email\":\"%s\",\"password\":\"%s\",\"remember\":%s}"
                                       .formatted(email, password, remember));
    }

    /** Signs in through the endpoint and hands back the session cookie, to send with later requests. */
    Cookie signIn(String email) throws Exception {
        MvcResult result = mockMvc.perform(login(postWithCsrf("/api/auth/login"), email, PASSWORD, false))
                                  .andExpect(status().isOk())
                                  .andReturn();
        return sessionCookie(result);
    }

    static Cookie sessionCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(SESSION_COOKIE);
        return new Cookie(SESSION_COOKIE, cookie.getValue());
    }

    /** The cookie carries the session id base64-encoded, which is Spring Session's default. */
    MapSession sessionOf(Cookie sessionCookie) {
        String id = new String(Base64.getDecoder().decode(sessionCookie.getValue()), StandardCharsets.UTF_8);
        return sessionRepository.findById(id);
    }
}
