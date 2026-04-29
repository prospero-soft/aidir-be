package ro.prospero.aidir.endpoint;


import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import ro.prospero.aidir.data.ToolDTO;
import ro.prospero.aidir.service.DirectusService;

import java.util.List;
import java.util.Optional;

@RestController
@AllArgsConstructor
@RequestMapping("api/tool")
public class ToolEndpoint {
    private DirectusService directusService;

    @GetMapping("all")
    @ResponseBody
    public List<ToolDTO> getAll(@RequestParam List<String> categories) {
        List<ToolDTO> tools = directusService.getAllTools();

        return tools;

    }

    @GetMapping()
    @ResponseBody
    public ToolDTO getOne(@RequestParam Long id) {
        return null;
    }

}
