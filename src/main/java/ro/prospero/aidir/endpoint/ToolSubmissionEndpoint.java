package ro.prospero.aidir.endpoint;

import lombok.AllArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import ro.prospero.aidir.data.ToolDTO;
import ro.prospero.aidir.data.ToolSubmissionDTO;
import ro.prospero.aidir.service.ToolSubmissionService;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("api/submission")
public class ToolSubmissionEndpoint {
    private ToolSubmissionService toolSubmissionService;


    @PostMapping
    public void save(@RequestBody ToolSubmissionDTO toolSubmissionDTO) {
        toolSubmissionService.saveSubmission(toolSubmissionDTO);
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
        queue.forEach(tool -> {
            toolSubmissionService.approve(tool);
        });
        return "OK";
    }



}
