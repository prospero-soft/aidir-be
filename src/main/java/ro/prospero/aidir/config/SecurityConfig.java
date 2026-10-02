package ro.prospero.aidir.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.header.writers.CacheControlHeadersWriter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import ro.prospero.aidir.security.SpaCsrfTokenRequestHandler;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Authentication is a server-side session carried in a cookie, and this is where the API decides who
 * may call what.
 *
 * <p>There are two filter chains and exactly one is active. Without the {@code dev} profile the rules
 * below are enforced. With it everything is open and CSRF is off, so that endpoints can be called from
 * curl or a browser with no session - but signing in still works and still creates one, because both
 * chains share {@link #sessionAndErrors}.
 *
 * <p>Signing in is a JSON endpoint rather than {@code formLogin}, so nothing here authenticates a
 * request by itself. The beans the login endpoint needs to do that by hand - the
 * {@link AuthenticationManager}, the {@link SessionAuthenticationStrategy} and the
 * {@link SecurityContextRepository} - are declared here so that it and the filter chain cannot end up
 * with different ones.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityConfig.class);

    private static final String LOGOUT_URL = "/api/auth/logout";
    private static final String ADMIN = "ADMIN";

    private final UploadsConfig uploadsConfig;
    private final ObjectMapper objectMapper;
    private final boolean cookieSecure;

    public SecurityConfig(UploadsConfig uploadsConfig,
                          ObjectMapper objectMapper,
                          @Value("${aidir.session.cookie-secure}") boolean cookieSecure) {
        this.uploadsConfig = uploadsConfig;
        this.objectMapper = objectMapper;
        this.cookieSecure = cookieSecure;
    }

    /**
     * Plain bcrypt rather than Spring's delegating encoder: the hashes onboarding has stored carry no
     * {@code {bcrypt}} prefix, and the delegating encoder would refuse every one of them.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * The provider answers an unknown email and a wrong password identically, and spends the same
     * time on both. The manager erases the password hash from the principal once it has matched.
     */
    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    /**
     * Spring Security does not save the security context on its own, so the login endpoint saves it
     * through this, and the filter chain reads it back from the same place.
     */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /**
     * The token lives in a cookie the front end can read. Its Secure flag comes from the same
     * property as the session cookie's, so the two never disagree.
     */
    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.secure(cookieSecure).sameSite("Lax"));
        return repository;
    }

    /**
     * What has to happen to the session when someone signs in: a new session id, against fixation, and
     * a new CSRF token. Under the {@code dev} profile there is no CSRF cookie to rotate and the second
     * half does nothing.
     */
    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrfTokenRepository) {
        CsrfAuthenticationStrategy csrf = new CsrfAuthenticationStrategy(csrfTokenRepository);
        csrf.setRequestHandler(new SpaCsrfTokenRequestHandler());
        return new CompositeSessionAuthenticationStrategy(List.of(new ChangeSessionIdAuthenticationStrategy(),
                                                                  csrf));
    }

    /**
     * Anything not listed needs a session, so a new endpoint is closed until it is given a rule here.
     * The moderation and maintenance endpoints are the admin's.
     */
    @Bean
    @Profile("!dev")
    public SecurityFilterChain enforcedFilterChain(HttpSecurity http,
                                                   SecurityContextRepository securityContextRepository,
                                                   CsrfTokenRepository csrfTokenRepository) throws Exception {
        sessionAndErrors(http, securityContextRepository);

        http.csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository)
                              .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
            .authorizeHttpRequests(authorize -> authorize
                    .requestMatchers(HttpMethod.GET,
                                     "/api/tool/**",
                                     "/api/talent/**",
                                     "/api/category",
                                     "/api/search",
                                     "/api/search/preview").permitAll()
                    .requestMatchers(HttpMethod.POST,
                                     "/api/user-onboarding/**",
                                     "/api/vendor-onboarding").permitAll()
                    // Open because the handlers answer for themselves: "me" is a 401 with no session.
                    .requestMatchers("/api/auth/login", "/api/auth/me", LOGOUT_URL).permitAll()
                    .requestMatchers(mediaPattern(), "/error", "/actuator/health").permitAll()
                    .requestMatchers("/api/submission/**",
                                     "/api/search/reindex",
                                     "/api/directus/**",
                                     "/actuator/**").hasRole(ADMIN)
                    .anyRequest().authenticated());

        return http.build();
    }

    @Bean
    @Profile("dev")
    public SecurityFilterChain devFilterChain(HttpSecurity http,
                                              SecurityContextRepository securityContextRepository) throws Exception {
        LOGGER.warn("The 'dev' profile is active: every endpoint is open to anyone who can reach this "
                    + "server, admin ones included, and CSRF protection is off.");

        sessionAndErrors(http, securityContextRepository);

        http.csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());

        return http.build();
    }

    private void sessionAndErrors(HttpSecurity http,
                                  SecurityContextRepository securityContextRepository) throws Exception {
        http.securityContext(context -> context.securityContextRepository(securityContextRepository))
            // The default cache parks a rejected request in the session so a login page can replay it.
            // There is no login page, and it would give every anonymous 401 a row in spring_session.
            .requestCache(RequestCacheConfigurer::disable)
            .logout(logout -> logout
                    .logoutUrl(LOGOUT_URL)
                    .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
            .exceptionHandling(handling -> handling
                    .authenticationEntryPoint(this::onUnauthenticated)
                    .accessDeniedHandler(this::onAccessDenied))
            // Spring Security marks every response uncacheable unless told otherwise, which is right
            // for the API and would make the browser refetch every uploaded image on every page.
            .headers(headers -> headers
                    .cacheControl(cacheControl -> cacheControl.disable())
                    .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                            new NegatedRequestMatcher(PathPatternRequestMatcher.withDefaults()
                                                                               .matcher(mediaPattern())),
                            new CacheControlHeadersWriter())));
    }

    private String mediaPattern() {
        return uploadsConfig.getPublicBasePath() + "/**";
    }

    private void onUnauthenticated(HttpServletRequest request, HttpServletResponse response,
                                   AuthenticationException exception) throws IOException {
        writeProblem(request, response, HttpStatus.UNAUTHORIZED, "You need to sign in to do that.");
    }

    /** A missing or stale CSRF token arrives here too, and says so: it is a client bug, not a permission. */
    private void onAccessDenied(HttpServletRequest request, HttpServletResponse response,
                                AccessDeniedException exception) throws IOException {
        String detail = exception instanceof CsrfException
                ? "The CSRF token is missing or does not match."
                : "Your account is not allowed to do that.";
        writeProblem(request, response, HttpStatus.FORBIDDEN, detail);
    }

    /**
     * These failures happen in the filter chain, before any controller, so ApiExceptionHandler never
     * sees them. Writing the same ProblemDetail shape by hand keeps one error format for the front end.
     */
    private void writeProblem(HttpServletRequest request, HttpServletResponse response,
                              HttpStatus status, String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setInstance(URI.create(request.getRequestURI()));

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
