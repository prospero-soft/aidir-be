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
import ro.prospero.aidir.data.ArticleSubmissionPayload;
import ro.prospero.aidir.data.ReviewNoteRequest;
import ro.prospero.aidir.data.SubmittedArticleDTO;
import ro.prospero.aidir.data.SubmittedArticleReviewDTO;
import ro.prospero.aidir.exception.ApiPayloadValidationException;
import ro.prospero.aidir.service.SubmittedArticleReviewService;

import java.util.List;
import java.util.Set;

/**
 * The compliance review of vendor-written articles, and the development helpers that fill and clear its queue.
 * Admin only, which SecurityConfig enforces for everything under {@code /api/admin}. The helpers' multipart
 * bodies are laid out as the vendor's own route takes them.
 */
@RestController
@RequestMapping(path = "api/admin/articles", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminArticleEndpoint {
    private final SubmittedArticleReviewService reviewService;
    private final ArticleMultipartReader multipartReader;
    private final Validator validator;

    public AdminArticleEndpoint(SubmittedArticleReviewService reviewService, ArticleMultipartReader multipartReader,
                                Validator validator) {
        this.reviewService = reviewService;
        this.multipartReader = multipartReader;
        this.validator = validator;
    }

    @GetMapping("queue")
    public List<SubmittedArticleReviewDTO> queue() {
        return reviewService.queue();
    }

    @PostMapping("{id}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(@PathVariable long id) {
        reviewService.approve(id);
    }

    /** The note is the compliance issues the vendor is to fix before submitting the article again. */
    @PostMapping(path = "{id}/requestChanges", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void requestChanges(@PathVariable long id, @RequestBody ReviewNoteRequest body) {
        Set<ConstraintViolation<ReviewNoteRequest>> violations = validator.validate(body);
        if (!violations.isEmpty()) {
            throw new ApiPayloadValidationException("Validating the review notes failed.", violations);
        }
        reviewService.requestChanges(id, body.note());
    }

    @PostMapping("devApproveAll")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approveAllInQueue() {
        reviewService.devApproveAll();
    }

    /** A synthetic vendor-written article, submitted for the vendor at {@code vendorIndex}. */
    @PostMapping(path = "devSubmit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public SubmittedArticleDTO devSubmit(@RequestParam int vendorIndex,
                                         @RequestPart("payload") String payloadJson,
                                         MultipartRequest request) throws JsonProcessingException {
        return reviewService.devSubmitAt(vendorIndex,
                                         multipartReader.payload(payloadJson, ArticleSubmissionPayload.class,
                                                                 "article"),
                                         multipartReader.featuredImage(request),
                                         multipartReader.additionalImages(request));
    }

    /** A synthetic vendor-written article, submitted for the vendor account with {@code vendorEmail}. */
    @PostMapping(path = "devSubmitAs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public SubmittedArticleDTO devSubmitAs(@RequestParam String vendorEmail,
                                           @RequestPart("payload") String payloadJson,
                                           MultipartRequest request) throws JsonProcessingException {
        return reviewService.devSubmitAs(vendorEmail,
                                         multipartReader.payload(payloadJson, ArticleSubmissionPayload.class,
                                                                 "article"),
                                         multipartReader.featuredImage(request),
                                         multipartReader.additionalImages(request));
    }
}
