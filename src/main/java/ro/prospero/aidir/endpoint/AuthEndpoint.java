package ro.prospero.aidir.endpoint;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.security.web.authentication.SpringSessionRememberMeServices;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.prospero.aidir.data.LoginRequest;
import ro.prospero.aidir.data.PrincipalDTO;
import ro.prospero.aidir.exception.ApiPayloadValidationException;
import ro.prospero.aidir.security.AccountPrincipal;
import ro.prospero.aidir.service.AccountService;

import java.time.Duration;
import java.util.Set;

/**
 * Signing in, and asking who is signed in. Signing out is not here: it is Spring Security's logout
 * filter, on {@code /api/auth/logout}, configured in {@code SecurityConfig}.
 */
@RestController
@RequestMapping("api/auth")
public class AuthEndpoint {
    private static final Duration REMEMBERED_TIMEOUT = Duration.ofDays(30);

    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final AccountService accountService;
    private final Validator validator;
    private final Duration sessionTimeout;

    public AuthEndpoint(AuthenticationManager authenticationManager,
                        SessionAuthenticationStrategy sessionAuthenticationStrategy,
                        SecurityContextRepository securityContextRepository,
                        AccountService accountService,
                        Validator validator,
                        @Value("${spring.session.timeout}") Duration sessionTimeout) {
        this.authenticationManager = authenticationManager;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.securityContextRepository = securityContextRepository;
        this.accountService = accountService;
        this.validator = validator;
        this.sessionTimeout = sessionTimeout;
    }

    /**
     * What {@code formLogin} would do, by hand, because the credentials arrive as JSON: authenticate,
     * give the session a new id and a new CSRF token, and save the security context into it. Nothing
     * else saves the context, so skipping that last step signs the caller in for this request only.
     *
     * <p>"Remember me" is a longer-lived session rather than a second token. The idle timeout is set
     * either way, because signing in again reuses the session that is already there, and a remembered
     * one would otherwise keep its thirty days.
     */
    @PostMapping(path = "login", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public PrincipalDTO login(@RequestBody LoginRequest login,
                              HttpServletRequest request,
                              HttpServletResponse response) {
        validate(login);

        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(login.email(), login.password()));
        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        Duration timeout = login.remember() ? REMEMBERED_TIMEOUT : sessionTimeout;
        request.getSession().setMaxInactiveInterval((int) timeout.toSeconds());
        if (login.remember()) {
            // Spring Session's cookie serializer looks for this when it writes the session cookie, and
            // gives the cookie a Max-Age so that it outlives the browser.
            request.setAttribute(SpringSessionRememberMeServices.REMEMBER_ME_LOGIN_ATTR, true);
        }

        AccountPrincipal principal = (AccountPrincipal) authentication.getPrincipal();
        return accountService.principalOf(principal.getAccountId())
                             .orElseThrow(() -> new IllegalStateException(
                                     "Account " + principal.getAccountId() + " signed in and has no row"));
    }

    /**
     * Answers 401 itself rather than through an {@code authenticated()} rule, because under the
     * {@code dev} profile there are no rules and the front end still needs to be told nobody is signed
     * in. The exception is left for the security filter chain, which writes the same body as for any
     * other request that needed a session.
     */
    @GetMapping(path = "me", produces = MediaType.APPLICATION_JSON_VALUE)
    public PrincipalDTO me(@AuthenticationPrincipal AccountPrincipal principal) {
        if (principal == null) {
            throw new AuthenticationCredentialsNotFoundException("No session");
        }
        return accountService.principalOf(principal.getAccountId())
                             .orElseThrow(() -> new AuthenticationCredentialsNotFoundException(
                                     "The session's account no longer exists"));
    }

    /** One message for an unknown email and a wrong password, so the answer does not say which it was. */
    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail onBadCredentials() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Incorrect email or password.");
    }

    private void validate(LoginRequest login) {
        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(login);

        if (!violations.isEmpty()) {
            throw new ApiPayloadValidationException("Validating the sign-in request failed.", violations);
        }
    }
}
