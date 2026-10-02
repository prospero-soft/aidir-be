package ro.prospero.aidir.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import ro.prospero.aidir.service.AccountService;

@Service
public class AccountUserDetailsService implements UserDetailsService {

    private final AccountService accountService;

    public AccountUserDetailsService(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * The username is the account's email. The message never reaches the caller: the authentication
     * provider turns a missing account into the same failure as a wrong password.
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return accountService.findByEmail(email)
                             .map(AccountPrincipal::of)
                             .orElseThrow(() -> new UsernameNotFoundException("No account for that email"));
    }
}
