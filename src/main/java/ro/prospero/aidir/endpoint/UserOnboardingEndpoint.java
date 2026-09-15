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
import ro.prospero.aidir.data.FreeUserOnboardingPayload;
import ro.prospero.aidir.data.UserOnboardingPayload;
import ro.prospero.aidir.data.UserOnboardingResult;
import ro.prospero.aidir.exception.ApiPayloadValidationException;
import ro.prospero.aidir.service.UserOnboardingService;

import java.util.Set;

/**
 * The two talent signups. They are separate routes rather than one payload with an optional profile
 * so that each contract is exact: a free signup has no field in which to carry a profile, and a paid
 * one cannot omit it.
 */
@RestController
@RequestMapping("api/user-onboarding")
public class UserOnboardingEndpoint {
    private final UserOnboardingService userOnboardingService;
    private final Validator validator;

    public UserOnboardingEndpoint(UserOnboardingService userOnboardingService, Validator validator) {
        this.userOnboardingService = userOnboardingService;
        this.validator = validator;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UserOnboardingResult save(@RequestBody UserOnboardingPayload payload) {
        validate(payload);
        return userOnboardingService.onboard(payload);
    }

    @PostMapping(path = "free", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UserOnboardingResult saveFree(@RequestBody FreeUserOnboardingPayload payload) {
        validate(payload);
        return userOnboardingService.onboardFree(payload);
    }

    private <T> void validate(T payload) {
        Set<ConstraintViolation<T>> violations = validator.validate(payload);

        if (!violations.isEmpty()) {
            throw new ApiPayloadValidationException("Validating user onboarding submission failed.", violations);
        }
    }
}
