package ro.prospero.aidir.endpoint;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartRequest;
import org.springframework.web.server.ResponseStatusException;
import ro.prospero.aidir.data.VendorToolDTO;
import ro.prospero.aidir.data.VendorToolEdit;
import ro.prospero.aidir.exception.ApiPayloadValidationException;
import ro.prospero.aidir.security.AccountPrincipal;
import ro.prospero.aidir.service.VendorToolService;

import java.util.Set;

import static ro.prospero.aidir.endpoint.VendorPrincipals.vendorId;

/**
 * The signed-in vendor's own listing, for the dashboard's AI Tool Profile page: read whole, written one card
 * at a time. Which listing is never in the request - it is always the session's.
 *
 * <p>Everything under {@code /api/vendor/**} needs a vendor session and a CSRF token outside the {@code dev}
 * profile; {@link VendorPrincipals} checks again for the profile where nothing is enforced.
 */
@RestController
@RequestMapping(path = "api/vendor/tool", produces = MediaType.APPLICATION_JSON_VALUE)
public class VendorToolEndpoint {
    private static final String LOGO_PART = "logo";
    private static final String SCREENSHOTS_PART = "screenshots";

    private final VendorToolService vendorToolService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public VendorToolEndpoint(VendorToolService vendorToolService, ObjectMapper objectMapper, Validator validator) {
        this.vendorToolService = vendorToolService;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    /** 404 until an approved listing exists: a vendor whose submission is still in review has nothing to edit. */
    @GetMapping
    public VendorToolDTO get(@AuthenticationPrincipal AccountPrincipal principal) {
        return vendorToolService.find(vendorId(principal))
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                                               "You have no listed tool yet"));
    }

    @PutMapping(path = "general", consumes = MediaType.APPLICATION_JSON_VALUE)
    public VendorToolDTO general(@AuthenticationPrincipal AccountPrincipal principal,
                                 @RequestBody VendorToolEdit.General body) {
        return vendorToolService.updateGeneral(vendorId(principal), validate(body));
    }

    @PutMapping(path = "overview", consumes = MediaType.APPLICATION_JSON_VALUE)
    public VendorToolDTO overview(@AuthenticationPrincipal AccountPrincipal principal,
                                  @RequestBody VendorToolEdit.Overview body) {
        return vendorToolService.updateOverview(vendorId(principal), validate(body));
    }

    @PutMapping(path = "features", consumes = MediaType.APPLICATION_JSON_VALUE)
    public VendorToolDTO features(@AuthenticationPrincipal AccountPrincipal principal,
                                  @RequestBody VendorToolEdit.Features body) {
        return vendorToolService.updateFeatures(vendorId(principal), validate(body));
    }

    @PutMapping(path = "integrations", consumes = MediaType.APPLICATION_JSON_VALUE)
    public VendorToolDTO integrations(@AuthenticationPrincipal AccountPrincipal principal,
                                      @RequestBody VendorToolEdit.Integrations body) {
        return vendorToolService.updateIntegrations(vendorId(principal), validate(body));
    }

    @PutMapping(path = "pricing", consumes = MediaType.APPLICATION_JSON_VALUE)
    public VendorToolDTO pricing(@AuthenticationPrincipal AccountPrincipal principal,
                                 @RequestBody VendorToolEdit.Pricing body) {
        return vendorToolService.updatePricing(vendorId(principal), validate(body));
    }

    @PutMapping(path = "faqs", consumes = MediaType.APPLICATION_JSON_VALUE)
    public VendorToolDTO faqs(@AuthenticationPrincipal AccountPrincipal principal,
                              @RequestBody VendorToolEdit.Faqs body) {
        return vendorToolService.updateFaqs(vendorId(principal), validate(body));
    }

    @PutMapping(path = "logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VendorToolDTO logo(@AuthenticationPrincipal AccountPrincipal principal, MultipartRequest request) {
        long vendorId = vendorId(principal);
        MultipartFile logo = request.getFile(LOGO_PART);
        return vendorToolService.replaceLogo(vendorId, logo == null || logo.isEmpty() ? null : logo);
    }

    /** A {@code payload} part with the demo URL and the screenshots to keep, and any new {@code screenshots}. */
    @PutMapping(path = "media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VendorToolDTO media(@AuthenticationPrincipal AccountPrincipal principal,
                               @RequestPart("payload") String payloadJson,
                               MultipartRequest request) throws JsonProcessingException {
        long vendorId = vendorId(principal);
        VendorToolEdit.Media media = validate(objectMapper.readValue(payloadJson, VendorToolEdit.Media.class));
        return vendorToolService.updateMedia(vendorId, media, request.getFiles(SCREENSHOTS_PART));
    }

    private <T> T validate(T body) {
        Set<ConstraintViolation<T>> violations = validator.validate(body);

        if (!violations.isEmpty()) {
            throw new ApiPayloadValidationException("Validating the listing edit failed.", violations);
        }
        return body;
    }
}
