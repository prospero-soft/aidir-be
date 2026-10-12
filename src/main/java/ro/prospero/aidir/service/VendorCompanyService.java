package ro.prospero.aidir.service;

import org.jooq.DSLContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ro.prospero.aidir.data.VendorCompanyDTO;

import static ro.prospero.aidir.jooq.generated.public_.Tables.ACCOUNT;
import static ro.prospero.aidir.jooq.generated.public_.Tables.COMPANY_INFORMATION;

/** The company behind a vendor account, assembled from what onboarding recorded and the current plan. */
@Service
public class VendorCompanyService {
    private final DSLContext dslContext;
    private final SubscriptionService subscriptionService;

    public VendorCompanyService(DSLContext dslContext, SubscriptionService subscriptionService) {
        this.dslContext = dslContext;
        this.subscriptionService = subscriptionService;
    }

    public VendorCompanyDTO get(long accountId) {
        var company = dslContext.select(ACCOUNT.ID, ACCOUNT.EMAIL, ACCOUNT.DISPLAY_NAME,
                                        COMPANY_INFORMATION.OFFICIAL_NAME, COMPANY_INFORMATION.WEBSITE_URL)
                                .from(ACCOUNT)
                                .leftJoin(COMPANY_INFORMATION).on(COMPANY_INFORMATION.ACCOUNT_ID.eq(ACCOUNT.ID))
                                .where(ACCOUNT.ID.eq(accountId))
                                .fetchOptional()
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                                               "No account with id " + accountId));

        String planKey = subscriptionService.currentPlanName(accountId).orElse(null);
        String planStatus = planKey == null ? "none" : subscriptionService.inGrace(accountId) ? "grace" : "active";
        String companyName = company.value4() != null ? company.value4() : company.value3();

        return new VendorCompanyDTO(company.value1(), companyName, company.value5(), company.value2(),
                                    planKey, planStatus);
    }
}
