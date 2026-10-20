package ro.prospero.aidir.endpoint;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockPart;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Triage of article briefs and its development helpers, as far as who may reach them and what they carry. */
class AdminBriefEndpointTest extends EndpointSecurityTestBase {

    private static final String BRIEF = """
            {"headline":"Acme ships a faster writer","brief":"Cover the launch.","topics":["Enterprise"],
             "audience":null,"keyMessages":["It is fast","It is cheap","It is here"],"sources":[],
             "preferredDate":null,
             "options":{"writing":"short","links":1,"permanentLinks":false,"unlabelled":false},
             "consents":{"accurate":true,"terms":true},"quotedTotal":178}
            """;

    private static MockPart payloadPart(String json) {
        MockPart part = new MockPart("payload", json.getBytes(StandardCharsets.UTF_8));
        part.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return part;
    }

    @Test
    void aVendorCannotSeeTheQueue() throws Exception {
        mockMvc.perform(get("/api/admin/briefs/queue").cookie(signIn(VENDOR)))
               .andExpect(status().isForbidden());

        verifyNoInteractions(articleBriefTriageService);
    }

    @Test
    void anAdminSeesTheQueue() throws Exception {
        when(articleBriefTriageService.queue()).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/briefs/queue").cookie(signIn(ADMIN)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$").isArray());
    }

    @Test
    void acceptingAnswersWithNoContent() throws Exception {
        mockMvc.perform(postWithCsrf("/api/admin/briefs/4/accept").cookie(signIn(ADMIN)))
               .andExpect(status().isNoContent());

        verify(articleBriefTriageService).accept(4L);
    }

    @Test
    void decliningNeedsAReason() throws Exception {
        mockMvc.perform(postWithCsrf("/api/admin/briefs/4/decline").cookie(signIn(ADMIN))
                                                                    .contentType(MediaType.APPLICATION_JSON)
                                                                    .content("{\"note\":\"\"}"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.constraintViolations[0]").value("note: must not be blank"));

        verify(articleBriefTriageService, never()).decline(anyLong(), anyString());
    }

    @Test
    void decliningCarriesItsReason() throws Exception {
        mockMvc.perform(postWithCsrf("/api/admin/briefs/4/decline").cookie(signIn(ADMIN))
                                                                    .contentType(MediaType.APPLICATION_JSON)
                                                                    .content("{\"note\":\"We do not cover this.\"}"))
               .andExpect(status().isNoContent());

        verify(articleBriefTriageService).decline(4L, "We do not cover this.");
    }

    @Test
    void acceptingTheQueueAnswersWithNoContent() throws Exception {
        mockMvc.perform(postWithCsrf("/api/admin/briefs/devAcceptAll").cookie(signIn(ADMIN)))
               .andExpect(status().isNoContent());

        verify(articleBriefTriageService).devAcceptAll();
    }

    @Test
    void aVendorCannotPushTestBriefs() throws Exception {
        mockMvc.perform(multipartWithCsrf(HttpMethod.POST, "/api/admin/briefs/devPlace?vendorIndex=0")
                                .part(payloadPart(BRIEF))
                                .cookie(signIn(VENDOR)))
               .andExpect(status().isForbidden());

        verifyNoInteractions(articleBriefTriageService);
    }

    @Test
    void aPushedBriefReachesTheServiceWithItsVendorIndex() throws Exception {
        mockMvc.perform(multipartWithCsrf(HttpMethod.POST, "/api/admin/briefs/devPlace?vendorIndex=12")
                                .part(payloadPart(BRIEF))
                                .cookie(signIn(ADMIN)))
               .andExpect(status().isCreated());

        verify(articleBriefTriageService).devPlaceAt(eq(12), any(), eq(null));
    }
}
