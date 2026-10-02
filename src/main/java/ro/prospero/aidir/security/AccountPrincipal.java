package ro.prospero.aidir.security;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import ro.prospero.aidir.jooq.generated.public_.tables.records.AccountRecord;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * The signed-in account, as the session holds it. Endpoints take the caller from this with
 * {@code @AuthenticationPrincipal AccountPrincipal}.
 *
 * <p>Deliberately small. Name, plan and avatar are not here: they are resolved per call by
 * {@link ro.prospero.aidir.service.AccountService#principalOf}, so a plan change shows up without
 * signing in again.
 *
 * <p>Spring Session writes this into {@code spring_session_attributes} with Java serialisation, so a
 * change to its fields makes every live session unreadable. Bump {@code serialVersionUID} when that
 * happens, so the old sessions fail as a version mismatch rather than as something stranger.
 */
public final class AccountPrincipal implements UserDetails, CredentialsContainer {
    @Serial
    private static final long serialVersionUID = 1L;

    private final long accountId;
    private final String email;
    private final String accountType;
    private String password;

    public AccountPrincipal(long accountId, String email, String accountType, String password) {
        this.accountId = accountId;
        this.email = email;
        this.accountType = accountType;
        this.password = password;
    }

    public static AccountPrincipal of(AccountRecord account) {
        return new AccountPrincipal(account.getId(), account.getEmail(), account.getAccountType(),
                                    account.getPassword());
    }

    public long getAccountId() {
        return accountId;
    }

    public String getEmail() {
        return email;
    }

    public String getAccountType() {
        return accountType;
    }

    /** One role per account type: ROLE_TALENT, ROLE_VENDOR, ROLE_ADMIN. */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + accountType.toUpperCase(Locale.ROOT)));
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return password;
    }

    /** Called once authentication has succeeded, which keeps the hash out of the session table. */
    @Override
    public void eraseCredentials() {
        password = null;
    }
}
