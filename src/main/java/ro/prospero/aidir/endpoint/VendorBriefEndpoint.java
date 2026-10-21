package ro.prospero.aidir.endpoint;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartRequest;
import ro.prospero.aidir.data.ArticleBriefDTO;
import ro.prospero.aidir.data.ArticleBriefPayload;
import ro.prospero.aidir.data.ReviewNoteRequest;
import ro.prospero.aidir.exception.ApiPayloadValidationException;
import ro.prospero.aidir.security.AccountPrincipal;
import ro.prospero.aidir.service.ArticleBriefService;

import java.util.List;
import java.util.Set;

import static ro.prospero.aidir.endpoint.VendorPrincipals.vendorId;

/**
 * The signed-in vendor's briefs for articles our editors write. Placing one is multipart, laid out as
 * {@link ArticleMultipartReader} reads it, and answers with the brief as placed, its reference and price
 * included. A draft our editors sent is answered by approving it or asking for revisions.
 */
@RestController
@RequestMapping(path = "api/vendor/briefs", produces = MediaType.APPLICATION_JSON_VALUE)
public class VendorBriefEndpoint {
    private final ArticleBriefService briefService;
    private final ArticleMultipartReader multipartReader;
    private final Validator validator;

    public VendorBriefEndpoint(ArticleBriefService briefService, ArticleMultipartReader multipartReader,
                               Validator validator) {
        this.briefService = briefService;
        this.multipartReader = multipartReader;
        this.validator = validator;
    }

    @GetMapping
    public List<ArticleBriefDTO> list(@AuthenticationPrincipal AccountPrincipal principal) {
        return briefService.list(vendorId(principal));
    }

    @GetMapping("{id}")
    public ArticleBriefDTO get(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable long id) {
        return briefService.get(vendorId(principal), id);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ArticleBriefDTO place(@AuthenticationPrincipal AccountPrincipal principal,
                                 @RequestPart("payload") String payloadJson,
                                 MultipartRequest request) throws JsonProcessingException {
        long accountId = vendorId(principal);
        return briefService.place(accountId,
                                  multipartReader.payload(payloadJson, ArticleBriefPayload.class, "brief"),
                                  multipartReader.featuredImage(request));
    }

    @PostMapping("{id}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable long id) {
        briefService.approve(vendorId(principal), id);
    }

    /** The note is what the vendor wants changed; our editor writes the next round from it. */
    @PostMapping(path = "{id}/revisions", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void requestRevisions(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable long id,
                                 @RequestBody ReviewNoteRequest body) {
        long accountId = vendorId(principal);
        Set<ConstraintViolation<ReviewNoteRequest>> violations = validator.validate(body);
        if (!violations.isEmpty()) {
            throw new ApiPayloadValidationException("Validating the revisions failed.", violations);
        }
        briefService.requestRevisions(accountId, id, body.note());
    }
}
