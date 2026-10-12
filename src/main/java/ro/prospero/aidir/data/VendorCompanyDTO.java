package ro.prospero.aidir.data;

/**
 * The signed-in vendor's company, as the deal creation sidebar shows it. {@code clientId} is the account
 * id. {@code planKey} is null and {@code planStatus} is "none" for an account with no current period;
 * otherwise the status is "active", or "grace" while a failed payment is being retried. Mirrors
 * VendorCompanyDTO in aidir-fe (src/types/vendorCompany.ts).
 */
public record VendorCompanyDTO(long clientId,
                               String companyName,
                               String website,
                               String contactEmail,
                               String planKey,
                               String planStatus) {
}
