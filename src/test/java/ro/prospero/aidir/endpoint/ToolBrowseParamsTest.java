package ro.prospero.aidir.endpoint;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ro.prospero.aidir.data.ToolBrowseQuery;
import ro.prospero.aidir.data.ToolFilter;
import ro.prospero.aidir.data.ToolPage;
import ro.prospero.aidir.service.ToolBrowseService;
import ro.prospero.aidir.service.ToolDetailsService;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ToolBrowseParamsTest {

    private final ToolBrowseService browseService = mock(ToolBrowseService.class);
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ToolEndpoint(mock(ToolDetailsService.class), browseService))
            .build();

    private ToolBrowseQuery browse(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        when(browseService.browse(any())).thenReturn(new ToolPage(List.of(), 0, 0, 10, Map.of()));
        mvc.perform(request).andExpect(status().isOk());

        ArgumentCaptor<ToolBrowseQuery> query = ArgumentCaptor.forClass(ToolBrowseQuery.class);
        verify(browseService).browse(query.capture());
        return query.getValue();
    }

    @Test
    void keepsASingleValueWithACommaWhole() throws Exception {
        ToolBrowseQuery query = browse(get("/api/tool/browse").param("category", "Audio, Voice & Music"));

        assertThat(query.selected(ToolFilter.CATEGORY)).containsExactly("Audio, Voice & Music");
    }

    @Test
    void readsTheFreeTrialToggle() throws Exception {
        ToolBrowseQuery query = browse(get("/api/tool/browse").param("freeTrial", "true")
                                                              .param("pricing", "subscription"));

        assertThat(query.freeTrial()).isTrue();
        assertThat(query.selected(ToolFilter.PRICING)).containsExactly("subscription");
    }

    @Test
    void leavesTheFreeTrialToggleOffByDefault() throws Exception {
        assertThat(browse(get("/api/tool/browse")).freeTrial()).isFalse();
    }

    @Test
    void readsRepeatedValuesOfEveryFilter() throws Exception {
        ToolBrowseQuery query = browse(get("/api/tool/browse").param("platform", "Web", "iOS")
                                                              .param("priceRange", "10_50")
                                                              .param("targetUser", "SME")
                                                              .param("aiTechnology", "NLP"));

        assertThat(query.selected(ToolFilter.PLATFORM)).containsExactly("Web", "iOS");
        assertThat(query.selected(ToolFilter.PRICE_RANGE)).containsExactly("10_50");
        assertThat(query.selected(ToolFilter.TARGET_USER)).containsExactly("SME");
        assertThat(query.selected(ToolFilter.AI_TECHNOLOGY)).containsExactly("NLP");
        assertThat(query.selected(ToolFilter.INDUSTRY)).isEmpty();
    }
}
