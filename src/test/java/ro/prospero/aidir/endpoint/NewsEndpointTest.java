package ro.prospero.aidir.endpoint;

import org.junit.jupiter.api.Test;
import ro.prospero.aidir.data.NewsArticleDTO;
import ro.prospero.aidir.data.NewsBrowseQuery;
import ro.prospero.aidir.data.NewsPage;
import ro.prospero.aidir.data.NewsSort;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The news pages' endpoints as the web layer sees them: open to anyone, and how a request is read. */
class NewsEndpointTest extends EndpointSecurityTestBase {

    @Test
    void theListNeedsNoSessionAndDefaultsEveryParameter() throws Exception {
        when(newsService.browse(any())).thenReturn(new NewsPage(List.of(), 0, 0, NewsBrowseQuery.DEFAULT_SIZE));

        mockMvc.perform(get("/api/news"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.total").value(0));

        verify(newsService).browse(new NewsBrowseQuery(null, null, NewsSort.LATEST, List.of(), 0,
                                                       NewsBrowseQuery.DEFAULT_SIZE));
    }

    @Test
    void theListReadsItsFiltersInLowerCaseAndRepeatedExcludes() throws Exception {
        when(newsService.browse(any())).thenReturn(new NewsPage(List.of(), 0, 2, 5));

        mockMvc.perform(get("/api/news").param("query", " acme ").param("topic", "Enterprise")
                                        .param("sort", "oldest").param("exclude", "a", "b")
                                        .param("page", "2").param("size", "5"))
               .andExpect(status().isOk());

        verify(newsService).browse(new NewsBrowseQuery("acme", "Enterprise", NewsSort.OLDEST, List.of("a", "b"), 2, 5));
    }

    @Test
    void anUnknownSortIsA400() throws Exception {
        mockMvc.perform(get("/api/news").param("sort", "popular"))
               .andExpect(status().isBadRequest());
    }

    @Test
    void anArticleNeedsNoSession() throws Exception {
        when(newsService.find("acme-ships")).thenReturn(Optional.of(
                new NewsArticleDTO("acme-ships", "Acme ships", "It shipped.", List.of("AI News"), "Acme Ltd",
                                   OffsetDateTime.parse("2026-10-20T09:00:00Z"), 3, "/media/a.png", true,
                                   List.of(), List.of(), List.of(), true, List.of())));

        mockMvc.perform(get("/api/news/acme-ships"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.byline").value("Acme Ltd"))
               .andExpect(jsonPath("$.sponsored").value(true));
    }

    @Test
    void anArticleNotPublishedIsA404() throws Exception {
        when(newsService.find("not-yet")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/news/not-yet"))
               .andExpect(status().isNotFound());
    }
}
