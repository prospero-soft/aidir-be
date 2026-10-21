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
import org.springframework.web.bind.annotation.PutMapping;
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
import ro.prospero.aidir.data.BriefAssignRequest;
import ro.prospero.aidir.data.BriefDraftPayload;
import ro.prospero.aidir.data.EditorDTO;
import ro.prospero.aidir.data.ReviewNoteRequest;
import ro.prospero.aidir.exception.ApiPayloadValidationException;
import ro.prospero.aidir.security.AccountPrincipal;
import ro.prospero.aidir.service.ArticleBriefTriageService;
import ro.prospero.aidir.service.ArticleBriefWritingService;

import java.util.List;
import java.util.Set;

import static ro.prospero.aidir.endpoint.AdminPrincipals.adminId;

/**
 * Triage of the briefs vendors place, the writing desk that takes the accepted ones on to the client, and the
 * development helpers that fill and clear the triage queue. Admin only, which SecurityConfig enforces for
 * everything under {@code /api/admin}. The helpers' multipart bodies are laid out as the vendor's own route
 * takes them, and a draft's as a submitted article's revision is.
 */
@RestController
@RequestMapping(path = "api/admin/briefs", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminBriefEndpoint {
    private final ArticleBriefTriageService triageService;
    private final ArticleBriefWritingService writingService;
    private final ArticleMultipartReader multipartReader;
    private final Validator validator;

    public AdminBriefEndpoint(ArticleBriefTriageService triageService, ArticleBriefWritingService writingService,
                              ArticleMultipartReader multipartReader, Validator validator) {
        this.triageService = triageService;
        this.writingService = writingService;
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

    /** The accepted briefs until their client takes the article, the longest accepted first. */
    @GetMapping("desk")
    public List<ArticleBriefReviewDTO> desk(@AuthenticationPrincipal AccountPrincipal principal) {
        return writingService.desk(adminId(principal));
    }

    /** The admin accounts a brief can be assigned to. */
    @GetMapping("editors")
    public List<EditorDTO> editors() {
        return writingService.editors();
    }

    @GetMapping("{id}")
    public ArticleBriefReviewDTO get(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable long id) {
        return writingService.get(adminId(principal), id);
    }

    @PostMapping(path = "{id}/assign", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assign(@PathVariable long id, @RequestBody BriefAssignRequest body) {
        Set<ConstraintViolation<BriefAssignRequest>> violations = validator.validate(body);
        if (!violations.isEmpty()) {
            throw new ApiPayloadValidationException("Validating the assignment failed.", violations);
        }
        writingService.assign(id, body.editorId());
    }

    /** Saves the round the caller is writing, who has to be the brief's editor, and answers with the brief. */
    @PutMapping(path = "{id}/draft", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ArticleBriefReviewDTO saveDraft(@AuthenticationPrincipal AccountPrincipal principal,
                                           @PathVariable long id,
                                           @RequestPart("payload") String payloadJson,
                                           MultipartRequest request) throws JsonProcessingException {
        long editorId = adminId(principal);
        return writingService.saveDraft(editorId, id,
                                        multipartReader.payload(payloadJson, BriefDraftPayload.class, "draft"),
                                        multipartReader.featuredImage(request),
                                        multipartReader.additionalImages(request));
    }

    /** Sends the saved draft to the client. */
    @PostMapping("{id}/send")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void send(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable long id) {
        writingService.send(adminId(principal), id);
    }

    /**
     * A synthetic draft, laid out as {@code PUT {id}/draft} takes one, written and sent to the client by the
     * caller, who takes the brief over first if they do not hold it.
     */
    @PostMapping(path = "{id}/devSendDraft", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void devSendDraft(@AuthenticationPrincipal AccountPrincipal principal,
                             @PathVariable long id,
                             @RequestPart("payload") String payloadJson,
                             MultipartRequest request) throws JsonProcessingException {
        long editorId = adminId(principal);
        writingService.devSendDraft(editorId, id,
                                    multipartReader.payload(payloadJson, BriefDraftPayload.class, "draft"),
                                    multipartReader.featuredImage(request),
                                    multipartReader.additionalImages(request));
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
