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
import ro.prospero.aidir.data.DealDTO;
import ro.prospero.aidir.data.DealReviewDTO;
import ro.prospero.aidir.data.RejectDealRequest;
import ro.prospero.aidir.exception.ApiPayloadValidationException;
import ro.prospero.aidir.service.DealReviewService;

import java.util.List;
import java.util.Set;

/**
 * Deal moderation, and the development helpers that fill and clear the queue. Admin only, which
 * SecurityConfig enforces for everything under {@code /api/admin}.
 */
@RestController
@RequestMapping(path = "api/admin/deals", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminDealEndpoint {
    private final DealReviewService dealReviewService;
    private final DealRequestReader requestReader;
    private final Validator validator;

    public AdminDealEndpoint(DealReviewService dealReviewService, DealRequestReader requestReader,
                             Validator validator) {
        this.dealReviewService = dealReviewService;
        this.requestReader = requestReader;
        this.validator = validator;
    }

    @GetMapping("queue")
    public List<DealReviewDTO> queue() {
        return dealReviewService.queue();
    }

    @PostMapping("{id}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(@PathVariable long id) {
        dealReviewService.approve(id);
    }

    @PostMapping(path = "{id}/reject", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@PathVariable long id, @RequestBody RejectDealRequest body) {
        Set<ConstraintViolation<RejectDealRequest>> violations = validator.validate(body);
        if (!violations.isEmpty()) {
            throw new ApiPayloadValidationException("Validating the rejection failed.", violations);
        }
        dealReviewService.reject(id, body.reason());
    }

    @PostMapping("devApproveAll")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approveAllInQueue() {
        dealReviewService.devApproveAll();
    }

    /** A synthetic deal, created and submitted for the vendor at {@code vendorIndex}. */
    @PostMapping(path = "devSubmit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DealDTO devSubmit(@RequestParam int vendorIndex,
                             @RequestPart("payload") String payloadJson,
                             MultipartRequest request) throws JsonProcessingException {
        return dealReviewService.devSubmit(vendorIndex, requestReader.payload(payloadJson),
                                           requestReader.logo(request));
    }

    /** A synthetic deal, created and submitted for the vendor account with {@code vendorEmail}. */
    @PostMapping(path = "devSubmitAs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DealDTO devSubmitAs(@RequestParam String vendorEmail,
                               @RequestPart("payload") String payloadJson,
                               MultipartRequest request) throws JsonProcessingException {
        return dealReviewService.devSubmitAs(vendorEmail, requestReader.payload(payloadJson),
                                             requestReader.logo(request));
    }
}
