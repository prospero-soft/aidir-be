package ro.prospero.aidir.endpoint;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ro.prospero.aidir.data.VendorOnboardingResult;
import ro.prospero.aidir.data.VendorSubmissionPayload;
import ro.prospero.aidir.exception.ApiPayloadValidationException;
import ro.prospero.aidir.service.VendorOnboardingService;

import java.util.List;
import java.util.Set;


@RestController
@RequestMapping("api/vendor-onboarding")
public class VendorOnboardingEndpoint {
    private final VendorOnboardingService vendorOnboardingService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public VendorOnboardingEndpoint(VendorOnboardingService vendorOnboardingService,
                                    ObjectMapper objectMapper,
                                    Validator validator) {
        this.vendorOnboardingService = vendorOnboardingService;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public VendorOnboardingResult save(@RequestPart("payload") String payloadJson,
                                       @RequestPart(name = "logo", required = false) MultipartFile logo,
                                       @RequestPart(name = "screenshots", required = false) List<MultipartFile> screenshots)
            throws JsonProcessingException {
        VendorSubmissionPayload payload = objectMapper.readValue(payloadJson, VendorSubmissionPayload.class);
        Set<ConstraintViolation<VendorSubmissionPayload>> violations = validator.validate(payload);

        if (!violations.isEmpty()) {
            throw new ApiPayloadValidationException("Validating the vendor onboarding submission failed.",
                                                    violations);
        }

        return vendorOnboardingService.onboard(payload, logo, screenshots);
    }
}
