package ro.prospero.aidir.exception;

import jakarta.validation.ConstraintViolation;
import lombok.Getter;

import java.util.List;
import java.util.Set;

public class ApiPayloadValidationException extends RuntimeException {
    @Getter
    private final List<String> constraintViolations;

    public ApiPayloadValidationException(String message, List<String> constraintViolations) {
        super(message);
        this.constraintViolations = constraintViolations;
    }

    /**
     * Both onboarding flows report through here so their failures come out identically formatted:
     * "&lt;property path&gt;: &lt;message&gt;", sorted, because validation order is not deterministic.
     * The path is what makes a violation actionable for a multi-step form - "must not be blank" on its
     * own does not say which step to send the user back to.
     */
    public ApiPayloadValidationException(String message, Set<? extends ConstraintViolation<?>> violations) {
        this(message, violations.stream()
                                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                                .sorted()
                                .toList());
    }
}
