package ro.prospero.aidir.endpoint;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import ro.prospero.aidir.security.AccountPrincipal;

/**
 * The vendor a {@code /api/vendor/**} request acts for. SecurityConfig already keeps everyone else out, but
 * not under the {@code dev} profile, where nothing is enforced - hence the checks here as well. Both
 * exceptions reach the security filter chain's handlers, so they answer as a 401 or 403 there would.
 */
final class VendorPrincipals {
    private static final String VENDOR = "vendor";

    private VendorPrincipals() {
    }

    static long vendorId(AccountPrincipal principal) {
        if (principal == null) {
            throw new AuthenticationCredentialsNotFoundException("No session");
        }
        if (!VENDOR.equals(principal.getAccountType())) {
            throw new AccessDeniedException("Only vendor accounts have deals");
        }
        return principal.getAccountId();
    }
}
