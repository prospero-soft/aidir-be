package ro.prospero.aidir.service;

import org.jooq.DSLContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static ro.prospero.aidir.jooq.generated.public_.Tables.ACCOUNT;

/**
 * The vendor accounts the development helpers act for: the admin has no deals or articles of its own, so the
 * dev console's pushers name a vendor, either by position or by email. Meant for development.
 */
@Component
public class DevVendorAccounts {
    private static final String VENDOR = "vendor";

    private final DSLContext dslContext;
    private final AccountService accountService;

    public DevVendorAccounts(DSLContext dslContext, AccountService accountService) {
        this.dslContext = dslContext;
        this.accountService = accountService;
    }

    /** The vendor at {@code index} in id order, wrapping around, so a run of pushes spreads over every vendor. */
    public long at(int index) {
        List<Long> vendors = dslContext.select(ACCOUNT.ID)
                                       .from(ACCOUNT)
                                       .where(ACCOUNT.ACCOUNT_TYPE.eq(VENDOR))
                                       .orderBy(ACCOUNT.ID.asc())
                                       .fetch(ACCOUNT.ID);
        if (vendors.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                              "There are no vendor accounts to submit for. "
                                              + "Push vendor onboarding test data first.");
        }
        return vendors.get(Math.floorMod(index, vendors.size()));
    }

    /** The vendor account with {@code email}, so a developer can sign in as it and find what was pushed. */
    public long byEmail(String email) {
        return accountService.findByEmail(email)
                             .filter(account -> VENDOR.equals(account.getAccountType()))
                             .orElseThrow(() -> new ResponseStatusException(
                                     HttpStatus.BAD_REQUEST,
                                     "No vendor account " + email + ". Create the test accounts first."))
                             .getId();
    }
}
