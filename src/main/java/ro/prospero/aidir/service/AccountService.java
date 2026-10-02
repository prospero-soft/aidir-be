package ro.prospero.aidir.service;

import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import ro.prospero.aidir.data.PrincipalDTO;
import ro.prospero.aidir.jooq.generated.public_.tables.records.AccountRecord;

import java.util.Locale;
import java.util.Optional;

import static ro.prospero.aidir.jooq.generated.public_.Tables.ACCOUNT;

/**
 * Accounts as sign-in sees them: looked up by email, and described to the front end as a principal.
 */
@Service
public class AccountService {

    private final DSLContext dslContext;
    private final SubscriptionService subscriptionService;

    public AccountService(DSLContext dslContext, SubscriptionService subscriptionService) {
        this.dslContext = dslContext;
        this.subscriptionService = subscriptionService;
    }

    /**
     * The form an email is stored and looked up in. Everything that writes or matches
     * {@code account.email} goes through this, which is what makes sign-in case-insensitive.
     * {@link Locale#ROOT} keeps the result independent of the server's locale - under a Turkish one
     * an "I" would otherwise lower-case to a dotless "ı".
     */
    public static String normalizeEmail(String email) {
        return email.toLowerCase(Locale.ROOT);
    }

    public Optional<AccountRecord> findByEmail(String email) {
        return dslContext.selectFrom(ACCOUNT)
                         .where(ACCOUNT.EMAIL.eq(normalizeEmail(email)))
                         .fetchOptional();
    }

    /**
     * Resolved per call rather than kept in the session, so a plan change shows up without signing in
     * again. Empty when the account no longer exists, which a session outliving its account can ask for.
     */
    public Optional<PrincipalDTO> principalOf(long accountId) {
        return dslContext.selectFrom(ACCOUNT)
                         .where(ACCOUNT.ID.eq(accountId))
                         .fetchOptional()
                         .map(account -> new PrincipalDTO(account.getId(),
                                                          account.getEmail(),
                                                          account.getDisplayName(),
                                                          account.getAccountType(),
                                                          subscriptionService.currentPlanName(accountId)
                                                                             .orElse(null),
                                                          null));
    }
}
