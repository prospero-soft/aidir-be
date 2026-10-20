package ro.prospero.aidir.endpoint;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartRequest;
import ro.prospero.aidir.exception.ApiPayloadValidationException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads the multipart requests that submit an article and place a brief: a {@code payload} part holding the
 * JSON, a {@code featured-image} part, and for a submitted article an {@code additional-image-<n>} part per
 * in-article image, numbered as the body's image blocks number them.
 */
@Component
public class ArticleMultipartReader {
    static final String FEATURED_IMAGE_PART = "featured-image";
    private static final Pattern ADDITIONAL_IMAGE_PART = Pattern.compile("additional-image-(\\d{1,3})");

    private final ObjectMapper objectMapper;
    private final Validator validator;

    public ArticleMultipartReader(ObjectMapper objectMapper, Validator validator) {
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    /** @param subject what the payload is, for the message: "article" or "brief" */
    public <T> T payload(String payloadJson, Class<T> type, String subject) throws JsonProcessingException {
        T payload = objectMapper.readValue(payloadJson, type);
        Set<ConstraintViolation<T>> violations = validator.validate(payload);

        if (!violations.isEmpty()) {
            throw new ApiPayloadValidationException("Validating the " + subject + " failed.", violations);
        }
        return payload;
    }

    /** Null when the request carries none. */
    public MultipartFile featuredImage(MultipartRequest request) {
        MultipartFile file = request.getFile(FEATURED_IMAGE_PART);
        return file == null || file.isEmpty() ? null : file;
    }

    /** Every additional image part, however many there are - the service decides how many is too many. */
    public List<MultipartFile> additionalImages(MultipartRequest request) {
        return request.getFileMap().entrySet().stream()
                      .filter(entry -> !entry.getValue().isEmpty())
                      .map(entry -> Map.entry(ADDITIONAL_IMAGE_PART.matcher(entry.getKey()), entry.getValue()))
                      .filter(entry -> entry.getKey().matches())
                      .sorted(Comparator.comparingInt(entry -> Integer.parseInt(entry.getKey().group(1))))
                      .map(Map.Entry::getValue)
                      .toList();
    }
}
