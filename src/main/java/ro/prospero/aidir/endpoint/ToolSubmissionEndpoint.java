package ro.prospero.aidir.endpoint;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ro.prospero.aidir.data.ToolDTO;
import ro.prospero.aidir.data.VendorSubmissionPayload;
import ro.prospero.aidir.exception.ApiPayloadValidationException;
import ro.prospero.aidir.service.ToolSubmissionService;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("api/submission")
public class ToolSubmissionEndpoint {
    private final ToolSubmissionService toolSubmissionService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public ToolSubmissionEndpoint(ToolSubmissionService toolSubmissionService, Validator validator) {
        this.toolSubmissionService = toolSubmissionService;
        this.validator = validator;
        this.objectMapper = new ObjectMapper();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public void save(@RequestPart("payload") String payloadJson,
                     @RequestPart(name = "logo", required = false) MultipartFile logo,
                     @RequestPart(name = "screenshots", required = false) List<MultipartFile> screenshots)
            throws JsonProcessingException {
        VendorSubmissionPayload payload = objectMapper.readValue(payloadJson, VendorSubmissionPayload.class);
        Set<ConstraintViolation<VendorSubmissionPayload>> violations = validator.validate(payload);

        if (!violations.isEmpty()) {
            throw new ApiPayloadValidationException("Validating tool submission failed.", violations);
        }

        toolSubmissionService.saveSubmission(payload);
    }

    @GetMapping(value = "queue",
                produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<ToolDTO> getQueue() {
        return toolSubmissionService.getQueue();
    }

    @PostMapping("/approve")
    public void approve(@RequestBody ToolDTO toolDTO) { // todo: should probably only take the id
        toolSubmissionService.approve(toolDTO);
    }

    @PostMapping("/reject")
    public void reject(@RequestBody ToolDTO toolDTO) {
        toolSubmissionService.reject(toolDTO);
    }

    //fixme: should stay dev only
    @GetMapping(value = "/devApproveAll")
    @ResponseBody
    public String approveAllInQueue() {
        List<ToolDTO> queue = toolSubmissionService.getQueue();
        queue.forEach(toolSubmissionService::approve);
        return "OK";
    }



}
