package ro.prospero.aidir.endpoint;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ro.prospero.aidir.data.NewsArticleDTO;
import ro.prospero.aidir.data.NewsBrowseQuery;
import ro.prospero.aidir.data.NewsPage;
import ro.prospero.aidir.data.NewsSort;
import ro.prospero.aidir.service.PublicNewsService;

import java.util.List;

/** The news pages' published articles. Open to anyone. */
@RestController
@RequestMapping(path = "api/news", produces = MediaType.APPLICATION_JSON_VALUE)
public class NewsEndpoint {
    private final PublicNewsService publicNewsService;

    public NewsEndpoint(PublicNewsService publicNewsService) {
        this.publicNewsService = publicNewsService;
    }

    /** Every parameter is optional; {@code exclude} repeats, one slug each. */
    @GetMapping
    public NewsPage browse(@RequestParam(required = false) String query,
                           @RequestParam(required = false) String topic,
                           @RequestParam(required = false) NewsSort sort,
                           @RequestParam(required = false) List<String> exclude,
                           @RequestParam(required = false) Integer page,
                           @RequestParam(required = false) Integer size) {
        return publicNewsService.browse(NewsBrowseQuery.of(query, topic, sort, exclude, page, size));
    }

    @GetMapping("{slug}")
    public NewsArticleDTO article(@PathVariable String slug) {
        return publicNewsService.find(slug)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                                               "No article at " + slug));
    }
}
