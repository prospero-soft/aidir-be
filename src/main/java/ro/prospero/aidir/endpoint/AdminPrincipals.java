package ro.prospero.aidir.endpoint;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import ro.prospero.aidir.security.AccountPrincipal;

/**
 * The admin an {@code /api/admin/**} request acts for, where the route needs to know which one. As with
 * {@link VendorPrincipals}, the checks repeat SecurityConfig's for the {@code dev} profile, which enforces
 * nothing.
 */
final class AdminPrincipals {
    private static final String ADMIN = "admin";

    private AdminPrincipals() {
    }

    static long adminId(AccountPrincipal principal) {
        if (principal == null) {
            throw new AuthenticationCredentialsNotFoundException("No session");
        }
        if (!ADMIN.equals(principal.getAccountType())) {
            throw new AccessDeniedException("Only admin accounts write briefs");
        }
        return principal.getAccountId();
    }
}
