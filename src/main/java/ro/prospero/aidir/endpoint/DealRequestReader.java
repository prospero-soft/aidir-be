package ro.prospero.aidir.endpoint;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartRequest;
import ro.prospero.aidir.data.DealPayload;
import ro.prospero.aidir.exception.ApiPayloadValidationException;

import java.util.Set;

/**
 * Reads the multipart request the deal form sends: a {@code payload} part holding the JSON, and a
 * {@code logo} part when a new logo was picked. Shared by the vendor's own save and the dev console's push,
 * so both are held to the same rules.
 */
@Component
public class DealRequestReader {
    static final String LOGO_PART = "logo";

    private final ObjectMapper objectMapper;
    private final Validator validator;

    public DealRequestReader(ObjectMapper objectMapper, Validator validator) {
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    public DealPayload payload(String payloadJson) throws JsonProcessingException {
        DealPayload payload = objectMapper.readValue(payloadJson, DealPayload.class);
        Set<ConstraintViolation<DealPayload>> violations = validator.validate(payload);

        if (!violations.isEmpty()) {
            throw new ApiPayloadValidationException("Validating the deal failed.", violations);
        }
        return payload;
    }

    /** The new logo, or null when the request carries none. */
    public MultipartFile logo(MultipartRequest request) {
        MultipartFile file = request.getFile(LOGO_PART);
        return file == null || file.isEmpty() ? null : file;
    }
}
