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
import ro.prospero.aidir.data.ArticleBriefDTO;
import ro.prospero.aidir.data.ArticleBriefPayload;
import ro.prospero.aidir.data.ArticleBriefReviewDTO;
import ro.prospero.aidir.data.ReviewNoteRequest;
import ro.prospero.aidir.exception.ApiPayloadValidationException;
import ro.prospero.aidir.service.ArticleBriefTriageService;

import java.util.List;
import java.util.Set;

/**
 * Triage of the briefs vendors place, and the development helpers that fill and clear its queue. Admin only,
 * which SecurityConfig enforces for everything under {@code /api/admin}. The helpers' multipart bodies are laid
 * out as the vendor's own route takes them.
 */
@RestController
@RequestMapping(path = "api/admin/briefs", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminBriefEndpoint {
    private final ArticleBriefTriageService triageService;
    private final ArticleMultipartReader multipartReader;
    private final Validator validator;

    public AdminBriefEndpoint(ArticleBriefTriageService triageService, ArticleMultipartReader multipartReader,
                              Validator validator) {
        this.triageService = triageService;
        this.multipartReader = multipartReader;
        this.validator = validator;
    }

    @GetMapping("queue")
    public List<ArticleBriefReviewDTO> queue() {
        return triageService.queue();
    }

    @PostMapping("{id}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(@PathVariable long id) {
        triageService.accept(id);
    }

    /** The note is why we will not write the article; the vendor is told. */
    @PostMapping(path = "{id}/decline", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decline(@PathVariable long id, @RequestBody ReviewNoteRequest body) {
        Set<ConstraintViolation<ReviewNoteRequest>> violations = validator.validate(body);
        if (!violations.isEmpty()) {
            throw new ApiPayloadValidationException("Validating the decline reason failed.", violations);
        }
        triageService.decline(id, body.note());
    }

    @PostMapping("devAcceptAll")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void acceptAllInQueue() {
        triageService.devAcceptAll();
    }

    /** A synthetic brief, placed for the vendor at {@code vendorIndex}. */
    @PostMapping(path = "devPlace", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ArticleBriefDTO devPlace(@RequestParam int vendorIndex,
                                    @RequestPart("payload") String payloadJson,
                                    MultipartRequest request) throws JsonProcessingException {
        return triageService.devPlaceAt(vendorIndex,
                                        multipartReader.payload(payloadJson, ArticleBriefPayload.class, "brief"),
                                        multipartReader.featuredImage(request));
    }

    /** A synthetic brief, placed for the vendor account with {@code vendorEmail}. */
    @PostMapping(path = "devPlaceAs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ArticleBriefDTO devPlaceAs(@RequestParam String vendorEmail,
                                      @RequestPart("payload") String payloadJson,
                                      MultipartRequest request) throws JsonProcessingException {
        return triageService.devPlaceAs(vendorEmail,
                                        multipartReader.payload(payloadJson, ArticleBriefPayload.class, "brief"),
                                        multipartReader.featuredImage(request));
    }
}
