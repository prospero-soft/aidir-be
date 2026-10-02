package ro.prospero.aidir.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

/**
 * CSRF for a single-page front end, as the Spring Security 6 reference describes it. The front end
 * reads the token from the {@code XSRF-TOKEN} cookie and sends it back raw in the
 * {@code X-XSRF-TOKEN} header.
 *
 * <p>{@code csrf().spa()} does the same thing but is Spring Security 7; this is that, by hand.
 */
public final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {
    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

    /**
     * The token is deferred, and the cookie is only written when something reads it. Reading it here
     * puts the cookie on every response, so it is there after an anonymous visitor's first GET - and
     * on the login response too, where the token has just been rotated and the old cookie cleared.
     */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       Supplier<CsrfToken> csrfToken) {
        xor.handle(request, response, csrfToken);
        csrfToken.get();
    }

    /**
     * A header carries the raw cookie value, which the plain handler compares as it is. Anything
     * else is a token rendered into a page, which is XOR-masked against BREACH and needs unmasking.
     */
    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        String headerValue = request.getHeader(csrfToken.getHeaderName());
        return (StringUtils.hasText(headerValue) ? plain : xor).resolveCsrfTokenValue(request, csrfToken);
    }
}
