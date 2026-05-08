package ro.prospero.aidir.endpoint;

import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import ro.prospero.aidir.data.ToolSearchHit;
import ro.prospero.aidir.service.DocumentIndexService;
import ro.prospero.aidir.service.DocumentSearchService;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("api/search")
public class SearchEndpoint {
    private DocumentSearchService documentSearchService;
    private DocumentIndexService documentIndexService;

    @GetMapping("preview")
    @ResponseBody
    public List<ToolSearchHit> autocomplete(@RequestParam String query) {
        return documentSearchService.searchAutocomplete(query, 5);
    }

    @GetMapping
    @ResponseBody
    public List<ToolSearchHit> search(@RequestParam String query) {
        return documentSearchService.search(query, 20);
    }

    //todo: add support for more complex search/filtering

    //fixme: should be auth-gated and admin-restricted once security is in
    @GetMapping("reindex")
    @ResponseBody
    public String reindex() {
        documentIndexService.rebuildAll();
        return "OK";
    }




}
