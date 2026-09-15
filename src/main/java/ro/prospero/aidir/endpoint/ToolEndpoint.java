package ro.prospero.aidir.endpoint;


import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ro.prospero.aidir.data.ToolDTO;
import ro.prospero.aidir.service.ToolService;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("api/tool")
public class ToolEndpoint {
    private ToolService toolService;

    @GetMapping("all")
    @ResponseBody
    public List<ToolDTO> getAll(@RequestParam List<String> categories) {
        List<ToolDTO> tools = toolService.getAll();

        return tools;

    }

    @GetMapping("details/{id}")
    @ResponseBody
    public ToolDTO getOne(@PathVariable Long id) {
        return toolService.find(id)
                          .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                                         "No tool with id " + id));
    }

}
