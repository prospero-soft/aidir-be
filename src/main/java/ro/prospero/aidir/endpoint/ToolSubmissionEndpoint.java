package ro.prospero.aidir.endpoint;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import ro.prospero.aidir.data.ToolDTO;
import ro.prospero.aidir.service.ToolSubmissionService;

import java.util.List;

/**
 * The moderation surface over the submission queue. Submissions are created by
 * {@code VendorOnboardingEndpoint}, which is where the vendor wizard posts.
 */
@RestController
@RequestMapping("api/submission")
public class ToolSubmissionEndpoint {
    private final ToolSubmissionService toolSubmissionService;

    public ToolSubmissionEndpoint(ToolSubmissionService toolSubmissionService) {
        this.toolSubmissionService = toolSubmissionService;
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
