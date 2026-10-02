package ro.prospero.aidir.config;

import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import ro.prospero.aidir.jooq.generated.public_.tables.records.AccountRecord;
import ro.prospero.aidir.service.AccountService;

import java.util.Optional;

import static ro.prospero.aidir.jooq.generated.public_.Tables.ACCOUNT;

/**
 * The only way an admin account comes to exist: no onboarding route creates one.
 *
 * <p>When {@code aidir.admin.email} and {@code aidir.admin.password} are both set and no account has
 * that email, one is created at startup. The password is read only then - changing the property later
 * does not change an existing admin's password. Neither property has a default; the {@code dev} profile
 * supplies both and any other environment sets them itself.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdminBootstrap.class);

    private static final String ADMIN = "admin";
    private static final String DISPLAY_NAME = "Administrator";

    private final DSLContext dslContext;
    private final AccountService accountService;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public AdminBootstrap(DSLContext dslContext,
                          AccountService accountService,
                          PasswordEncoder passwordEncoder,
                          @Value("${aidir.admin.email:}") String email,
                          @Value("${aidir.admin.password:}") String password) {
        this.dslContext = dslContext;
        this.accountService = accountService;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() && password.isBlank()) {
            return;
        }
        if (email.isBlank() || password.isBlank()) {
            LOGGER.warn("Only one of aidir.admin.email and aidir.admin.password is set, so no admin "
                        + "account was created");
            return;
        }

        Optional<AccountRecord> existing = accountService.findByEmail(email);
        if (existing.isPresent()) {
            if (!ADMIN.equals(existing.get().getAccountType())) {
                LOGGER.warn("aidir.admin.email names an existing '{}' account, which was left as it is",
                            existing.get().getAccountType());
            }
            return;
        }

        String normalizedEmail = AccountService.normalizeEmail(email);
        dslContext.insertInto(ACCOUNT)
                  .set(ACCOUNT.EMAIL, normalizedEmail)
                  .set(ACCOUNT.PASSWORD, passwordEncoder.encode(password))
                  .set(ACCOUNT.ACCOUNT_TYPE, ADMIN)
                  .set(ACCOUNT.DISPLAY_NAME, DISPLAY_NAME)
                  .execute();
        LOGGER.info("Created the admin account {}", normalizedEmail);
    }
}
