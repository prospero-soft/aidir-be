package ro.prospero.aidir.endpoint;

import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import ro.prospero.aidir.data.FacetCount;
import ro.prospero.aidir.service.ToolBrowseService;

import java.util.List;

/**
 * The category vocabulary, with how many published tools sit in each.
 *
 * <p>This is what the navigation and any unfiltered picker should read. The per-request counts on
 * {@code /api/tool/browse} are the ones a filter sidebar wants instead, because those already account for
 * the filters the user has applied.
 */
@RestController
@AllArgsConstructor
@RequestMapping("api/category")
public class CategoryEndpoint {
    private ToolBrowseService toolBrowseService;

    @GetMapping
    @ResponseBody
    public List<FacetCount> getAll() {
        return toolBrowseService.categoryCounts();
    }
}
