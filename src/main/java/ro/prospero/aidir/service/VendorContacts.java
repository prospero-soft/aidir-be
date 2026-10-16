package ro.prospero.aidir.service;

import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

import static ro.prospero.aidir.jooq.generated.public_.Tables.ACCOUNT;
import static ro.prospero.aidir.jooq.generated.public_.Tables.COMPANY_INFORMATION;

/** Who is behind the vendor accounts a review queue lists, so the reviewer knows whom they are judging. */
@Component
public class VendorContacts {
    private static final Contact UNKNOWN = new Contact(null, null);

    private final DSLContext dslContext;

    public VendorContacts(DSLContext dslContext) {
        this.dslContext = dslContext;
    }

    public record Contact(String name, String email) {
    }

    /**
     * The company's official name where onboarding recorded one, the account's display name otherwise. An
     * account that is not found answers with an empty contact rather than being left out.
     */
    public Map<Long, Contact> of(Collection<Long> accountIds) {
        return dslContext.select(ACCOUNT.ID, ACCOUNT.DISPLAY_NAME, COMPANY_INFORMATION.OFFICIAL_NAME, ACCOUNT.EMAIL)
                         .from(ACCOUNT)
                         .leftJoin(COMPANY_INFORMATION).on(COMPANY_INFORMATION.ACCOUNT_ID.eq(ACCOUNT.ID))
                         .where(ACCOUNT.ID.in(accountIds))
                         .fetchMap(ACCOUNT.ID, r -> new Contact(r.value3() != null ? r.value3() : r.value2(),
                                                                r.value4()));
    }

    public static Contact orUnknown(Map<Long, Contact> contacts, long accountId) {
        return contacts.getOrDefault(accountId, UNKNOWN);
    }
}
