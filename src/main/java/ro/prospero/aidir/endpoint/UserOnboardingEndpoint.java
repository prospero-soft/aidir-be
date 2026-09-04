package ro.prospero.aidir.endpoint;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ro.prospero.aidir.data.UserOnboardingPayload;
import ro.prospero.aidir.exception.ApiPayloadValidationException;
import ro.prospero.aidir.service.UserOnboardingService;

import java.util.Set;

@RestController
@RequestMapping("api/user-onboarding")
public class UserOnboardingEndpoint {
    private final UserOnboardingService userOnboardingService;
    private final Validator validator;

    public UserOnboardingEndpoint(UserOnboardingService userOnboardingService, Validator validator) {
        this.userOnboardingService = userOnboardingService;
        this.validator = validator;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public void save(@RequestBody UserOnboardingPayload payload) {
        Set<ConstraintViolation<UserOnboardingPayload>> violations = validator.validate(payload);

        if (!violations.isEmpty()) {
            throw new ApiPayloadValidationException("Validating user onboarding submission failed.", violations);
        }

        userOnboardingService.saveSubmission(payload);
    }
}
