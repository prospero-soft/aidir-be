package ro.prospero.aidir.endpoint;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartRequest;
import org.springframework.web.server.ResponseStatusException;
import ro.prospero.aidir.data.ArticleOrderDTO;
import ro.prospero.aidir.data.ArticleOrderKind;
import ro.prospero.aidir.data.ArticleRequestPayload;
import ro.prospero.aidir.data.ArticleReviewDTO;
import ro.prospero.aidir.data.ArticleSubmissionPayload;
import ro.prospero.aidir.data.RejectArticleRequest;
import ro.prospero.aidir.exception.ApiPayloadValidationException;
import ro.prospero.aidir.service.ArticleReviewService;

import java.util.List;
import java.util.Set;

/**
 * Sponsored article moderation, and the development helpers that fill and clear the queue. Admin only, which
 * SecurityConfig enforces for everything under {@code /api/admin}. The helpers' multipart bodies are laid out
 * as the vendor's own routes take them.
 */
@RestController
@RequestMapping(path = "api/admin/articles", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminArticleEndpoint {
    private final ArticleReviewService articleReviewService;
    private final ArticleRequestReader requestReader;
    private final Validator validator;

    public AdminArticleEndpoint(ArticleReviewService articleReviewService, ArticleRequestReader requestReader,
                                Validator validator) {
        this.articleReviewService = articleReviewService;
        this.requestReader = requestReader;
        this.validator = validator;
    }

    @GetMapping("queue")
    public List<ArticleReviewDTO> queue() {
        return articleReviewService.queue();
    }

    @PostMapping("{id}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(@PathVariable long id) {
        articleReviewService.approve(id);
    }

    @PostMapping(path = "{id}/reject", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@PathVariable long id, @RequestBody RejectArticleRequest body) {
        Set<ConstraintViolation<RejectArticleRequest>> violations = validator.validate(body);
        if (!violations.isEmpty()) {
            throw new ApiPayloadValidationException("Validating the rejection failed.", violations);
        }
        articleReviewService.reject(id, body.reason());
    }

    /** Every pending order, or only those of {@code kind} ({@code submitted} or {@code editorial}). */
    @PostMapping("devApproveAll")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approveAllInQueue(@RequestParam(required = false) String kind) {
        articleReviewService.devApproveAll(kind == null ? null : orderKind(kind));
    }

    /** A synthetic vendor-written article, placed for the vendor at {@code vendorIndex}. */
    @PostMapping(path = "devSubmit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ArticleOrderDTO devSubmit(@RequestParam int vendorIndex,
                                     @RequestPart("payload") String payloadJson,
                                     MultipartRequest request) throws JsonProcessingException {
        return articleReviewService.devSubmitArticleAt(vendorIndex,
                                                       requestReader.payload(payloadJson, ArticleSubmissionPayload.class),
                                                       requestReader.featuredImage(request),
                                                       requestReader.additionalImages(request));
    }

    /** A synthetic vendor-written article, placed for the vendor account with {@code vendorEmail}. */
    @PostMapping(path = "devSubmitAs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ArticleOrderDTO devSubmitAs(@RequestParam String vendorEmail,
                                       @RequestPart("payload") String payloadJson,
                                       MultipartRequest request) throws JsonProcessingException {
        return articleReviewService.devSubmitArticleAs(vendorEmail,
                                                       requestReader.payload(payloadJson, ArticleSubmissionPayload.class),
                                                       requestReader.featuredImage(request),
                                                       requestReader.additionalImages(request));
    }

    /** A synthetic brief for our editors, placed for the vendor at {@code vendorIndex}. */
    @PostMapping(path = "devRequest", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ArticleOrderDTO devRequest(@RequestParam int vendorIndex,
                                      @RequestPart("payload") String payloadJson,
                                      MultipartRequest request) throws JsonProcessingException {
        return articleReviewService.devRequestArticleAt(vendorIndex,
                                                        requestReader.payload(payloadJson, ArticleRequestPayload.class),
                                                        requestReader.featuredImage(request));
    }

    /** A synthetic brief for our editors, placed for the vendor account with {@code vendorEmail}. */
    @PostMapping(path = "devRequestAs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ArticleOrderDTO devRequestAs(@RequestParam String vendorEmail,
                                        @RequestPart("payload") String payloadJson,
                                        MultipartRequest request) throws JsonProcessingException {
        return articleReviewService.devRequestArticleAs(vendorEmail,
                                                        requestReader.payload(payloadJson, ArticleRequestPayload.class),
                                                        requestReader.featuredImage(request));
    }

    private static ArticleOrderKind orderKind(String kind) {
        try {
            return ArticleOrderKind.fromWire(kind);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
