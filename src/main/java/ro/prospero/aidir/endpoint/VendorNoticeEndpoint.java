package ro.prospero.aidir.endpoint;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.prospero.aidir.data.VendorNoticeDTO;
import ro.prospero.aidir.security.AccountPrincipal;
import ro.prospero.aidir.service.VendorNoticeService;

import java.util.List;

import static ro.prospero.aidir.endpoint.VendorPrincipals.vendorId;

/** What the signed-in vendor is asked to act on, for their dashboard. */
@RestController
@RequestMapping(path = "api/vendor/notices", produces = MediaType.APPLICATION_JSON_VALUE)
public class VendorNoticeEndpoint {
    private final VendorNoticeService noticeService;

    public VendorNoticeEndpoint(VendorNoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @GetMapping
    public List<VendorNoticeDTO> list(@AuthenticationPrincipal AccountPrincipal principal) {
        return noticeService.notices(vendorId(principal));
    }
}
