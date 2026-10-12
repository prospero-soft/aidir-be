package ro.prospero.aidir.endpoint;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.prospero.aidir.data.VendorCompanyDTO;
import ro.prospero.aidir.security.AccountPrincipal;
import ro.prospero.aidir.service.VendorCompanyService;

import static ro.prospero.aidir.endpoint.VendorPrincipals.vendorId;

/** The signed-in vendor's company, for the summary the deal creation page shows beside the form. */
@RestController
@RequestMapping(path = "api/vendor/company", produces = MediaType.APPLICATION_JSON_VALUE)
public class VendorCompanyEndpoint {
    private final VendorCompanyService vendorCompanyService;

    public VendorCompanyEndpoint(VendorCompanyService vendorCompanyService) {
        this.vendorCompanyService = vendorCompanyService;
    }

    @GetMapping
    public VendorCompanyDTO get(@AuthenticationPrincipal AccountPrincipal principal) {
        return vendorCompanyService.get(vendorId(principal));
    }
}
