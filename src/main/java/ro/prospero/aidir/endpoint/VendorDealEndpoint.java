package ro.prospero.aidir.endpoint;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartRequest;
import ro.prospero.aidir.data.DealDTO;
import ro.prospero.aidir.data.DealSummaryDTO;
import ro.prospero.aidir.security.AccountPrincipal;
import ro.prospero.aidir.service.DealService;

import java.util.List;

import static ro.prospero.aidir.endpoint.VendorPrincipals.vendorId;

/**
 * The signed-in vendor's deals. The account is always the session's, so the ids in these routes only ever
 * reach that vendor's own deals.
 *
 * <p>Saving takes the form as multipart - a {@code payload} part and a {@code logo-<placement>} part per
 * new logo - because the logos are files. Submitting takes nothing: it checks the deal as last saved.
 */
@RestController
@RequestMapping(path = "api/vendor/deals", produces = MediaType.APPLICATION_JSON_VALUE)
public class VendorDealEndpoint {
    private final DealService dealService;
    private final DealRequestReader requestReader;

    public VendorDealEndpoint(DealService dealService, DealRequestReader requestReader) {
        this.dealService = dealService;
        this.requestReader = requestReader;
    }

    @GetMapping
    public List<DealSummaryDTO> list(@AuthenticationPrincipal AccountPrincipal principal) {
        return dealService.list(vendorId(principal));
    }

    @GetMapping("{id}")
    public DealDTO get(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable long id) {
        return dealService.get(vendorId(principal), id);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DealDTO create(@AuthenticationPrincipal AccountPrincipal principal,
                          @RequestPart("payload") String payloadJson,
                          MultipartRequest request) throws JsonProcessingException {
        long accountId = vendorId(principal);
        return dealService.create(accountId, requestReader.payload(payloadJson), requestReader.logos(request));
    }

    @PutMapping(path = "{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DealDTO update(@AuthenticationPrincipal AccountPrincipal principal,
                          @PathVariable long id,
                          @RequestPart("payload") String payloadJson,
                          MultipartRequest request) throws JsonProcessingException {
        long accountId = vendorId(principal);
        return dealService.update(accountId, id, requestReader.payload(payloadJson), requestReader.logos(request));
    }

    @PostMapping("{id}/submit")
    public DealDTO submit(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable long id) {
        return dealService.submit(vendorId(principal), id);
    }

    @DeleteMapping("{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable long id) {
        dealService.delete(vendorId(principal), id);
    }
}
