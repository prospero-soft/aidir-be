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
import ro.prospero.aidir.data.ToolBrowseQuery;
import ro.prospero.aidir.data.ToolDTO;
import ro.prospero.aidir.data.ToolPage;
import ro.prospero.aidir.data.ToolSort;
import ro.prospero.aidir.service.ToolBrowseService;
import ro.prospero.aidir.service.ToolService;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("api/tool")
public class ToolEndpoint {
    private ToolService toolService;
    private ToolBrowseService toolBrowseService;

    /**
     * The directory. Every parameter is optional, and with none of them this is the front page of the
     * catalogue: browsing is the default, searching and filtering are what narrow it.
     *
     * <p>{@code category} and {@code pricing} repeat rather than taking a delimited list, so a value is
     * free to contain whatever punctuation a category name happens to have.
     */
    @GetMapping("browse")
    @ResponseBody
    public ToolPage browse(@RequestParam(required = false) String query,
                           @RequestParam(required = false) List<String> category,
                           @RequestParam(required = false) List<String> pricing,
                           @RequestParam(required = false) ToolSort sort,
                           @RequestParam(required = false) Integer page,
                           @RequestParam(required = false) Integer size) {
        return toolBrowseService.browse(ToolBrowseQuery.of(query, category, pricing, sort, page, size));
    }

    @GetMapping("details/{id}")
    @ResponseBody
    public ToolDTO getOne(@PathVariable Long id) {
        return toolService.find(id)
                          .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                                         "No tool with id " + id));
    }

}
