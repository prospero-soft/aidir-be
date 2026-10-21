package ro.prospero.aidir.endpoint;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.web.multipart.MultipartFile;
import ro.prospero.aidir.data.BriefDraftPayload;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Triage of article briefs, the writing desk and the development helpers, as far as who may reach them and what
 * they carry.
 */
class AdminBriefEndpointTest extends EndpointSecurityTestBase {
    private static final long ADMIN_ID = 2L;

    private static final String BRIEF = """
            {"headline":"Acme ships a faster writer","brief":"Cover the launch.","topics":["Enterprise"],
             "audience":null,"keyMessages":["It is fast","It is cheap","It is here"],"sources":[],
             "preferredDate":null,
             "options":{"writing":"short","links":1,"permanentLinks":false,"unlabelled":false},
             "consents":{"accurate":true,"terms":true},"quotedTotal":178}
            """;

    private static final String DRAFT = """
            {"headline":"Acme ships a faster writer",
             "body":[{"kind":"paragraph","content":[{"text":"Words.","href":null,"bold":false,"italic":false}]},
                     {"kind":"image","image":0,"alt":""},{"kind":"image","image":1,"alt":""}],
             "sources":[],"keptImages":[1,null]}
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

    @Test
    void aVendorCannotSeeTheDesk() throws Exception {
        mockMvc.perform(get("/api/admin/briefs/desk").cookie(signIn(VENDOR)))
               .andExpect(status().isForbidden());

        verifyNoInteractions(articleBriefWritingService);
    }

    @Test
    void theDeskIsReadForTheSignedInAdmin() throws Exception {
        when(articleBriefWritingService.desk(anyLong())).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/briefs/desk").cookie(signIn(ADMIN)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$").isArray());

        verify(articleBriefWritingService).desk(ADMIN_ID);
    }

    @Test
    void assigningNeedsAnEditor() throws Exception {
        mockMvc.perform(postWithCsrf("/api/admin/briefs/4/assign").cookie(signIn(ADMIN))
                                                                   .contentType(MediaType.APPLICATION_JSON)
                                                                   .content("{}"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.constraintViolations[0]").value("editorId: must not be null"));

        verifyNoInteractions(articleBriefWritingService);
    }

    @Test
    void assigningCarriesTheEditor() throws Exception {
        mockMvc.perform(postWithCsrf("/api/admin/briefs/4/assign").cookie(signIn(ADMIN))
                                                                   .contentType(MediaType.APPLICATION_JSON)
                                                                   .content("{\"editorId\":9}"))
               .andExpect(status().isNoContent());

        verify(articleBriefWritingService).assign(4L, 9L);
    }

    @Test
    void aVendorCannotSaveADraft() throws Exception {
        mockMvc.perform(multipartWithCsrf(HttpMethod.PUT, "/api/admin/briefs/4/draft")
                                .part(payloadPart(DRAFT))
                                .cookie(signIn(VENDOR)))
               .andExpect(status().isForbidden());

        verifyNoInteractions(articleBriefWritingService);
    }

    @Test
    void aDraftReachesTheServiceWithItsNewImagesInOrder() throws Exception {
        MockMultipartFile later = new MockMultipartFile("additional-image-10", "b.png", "image/png", new byte[]{2});
        MockMultipartFile earlier = new MockMultipartFile("additional-image-2", "a.png", "image/png", new byte[]{1});

        mockMvc.perform(multipartWithCsrf(HttpMethod.PUT, "/api/admin/briefs/4/draft")
                                .file(later)
                                .file(earlier)
                                .part(payloadPart(DRAFT))
                                .cookie(signIn(ADMIN)))
               .andExpect(status().isOk());

        verify(articleBriefWritingService).saveDraft(
                eq(ADMIN_ID), eq(4L),
                argThat((BriefDraftPayload payload) -> payload.keptImages().equals(Arrays.asList(1, null))),
                isNull(),
                argThat((List<MultipartFile> images) -> images.size() == 2
                                                        && "a.png".equals(images.get(0).getOriginalFilename())));
    }

    @Test
    void sendingAnswersWithNoContent() throws Exception {
        mockMvc.perform(postWithCsrf("/api/admin/briefs/4/send").cookie(signIn(ADMIN)))
               .andExpect(status().isNoContent());

        verify(articleBriefWritingService).send(ADMIN_ID, 4L);
    }

    @Test
    void aVendorCannotSendATestDraft() throws Exception {
        mockMvc.perform(multipartWithCsrf(HttpMethod.POST, "/api/admin/briefs/4/devSendDraft")
                                .part(payloadPart(DRAFT))
                                .cookie(signIn(VENDOR)))
               .andExpect(status().isForbidden());

        verifyNoInteractions(articleBriefWritingService);
    }

    @Test
    void aTestDraftReachesTheServiceForTheSignedInAdmin() throws Exception {
        MockMultipartFile cover = new MockMultipartFile("featured-image", "c.png", "image/png", new byte[]{3});

        mockMvc.perform(multipartWithCsrf(HttpMethod.POST, "/api/admin/briefs/4/devSendDraft")
                                .file(cover)
                                .part(payloadPart(DRAFT))
                                .cookie(signIn(ADMIN)))
               .andExpect(status().isNoContent());

        verify(articleBriefWritingService).devSendDraft(
                eq(ADMIN_ID), eq(4L),
                argThat((BriefDraftPayload payload) -> payload.headline().equals("Acme ships a faster writer")),
                argThat((MultipartFile file) -> "c.png".equals(file.getOriginalFilename())),
                argThat((List<MultipartFile> images) -> images.isEmpty()));
    }
}
