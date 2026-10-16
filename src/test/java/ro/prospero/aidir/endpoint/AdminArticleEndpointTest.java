package ro.prospero.aidir.endpoint;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import ro.prospero.aidir.data.ArticleOrderKind;

import java.nio.charset.StandardCharsets;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Article order moderation and its development helpers, as far as who may reach them and what they carry. */
class AdminArticleEndpointTest extends EndpointSecurityTestBase {

    @Test
    void aVendorCannotSeeTheQueue() throws Exception {
        mockMvc.perform(get("/api/admin/articles/queue").cookie(signIn(VENDOR)))
               .andExpect(status().isForbidden());

        verifyNoInteractions(articleReviewService);
    }

    @Test
    void anAdminSeesTheQueue() throws Exception {
        when(articleReviewService.queue()).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/articles/queue").cookie(signIn(ADMIN)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$").isArray());
    }

    @Test
    void approvingAnswersWithNoContent() throws Exception {
        mockMvc.perform(postWithCsrf("/api/admin/articles/9/approve").cookie(signIn(ADMIN)))
               .andExpect(status().isNoContent());

        verify(articleReviewService).approve(9L);
    }

    @Test
    void aRejectionNeedsAReason() throws Exception {
        mockMvc.perform(postWithCsrf("/api/admin/articles/9/reject").cookie(signIn(ADMIN))
                                                                     .contentType(MediaType.APPLICATION_JSON)
                                                                     .content("{\"reason\":\"  \"}"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.constraintViolations[0]").value("reason: must not be blank"));

        verify(articleReviewService, never()).reject(anyLong(), anyString());
    }

    @Test
    void aRejectionCarriesItsReason() throws Exception {
        mockMvc.perform(postWithCsrf("/api/admin/articles/9/reject").cookie(signIn(ADMIN))
                                                                     .contentType(MediaType.APPLICATION_JSON)
                                                                     .content("{\"reason\":\"Too promotional.\"}"))
               .andExpect(status().isNoContent());

        verify(articleReviewService).reject(9L, "Too promotional.");
    }

    @Test
    void approvingTheQueueCanBeNarrowedToOneKind() throws Exception {
        mockMvc.perform(postWithCsrf("/api/admin/articles/devApproveAll?kind=submitted").cookie(signIn(ADMIN)))
               .andExpect(status().isNoContent());

        verify(articleReviewService).devApproveAll(ArticleOrderKind.SUBMITTED);
    }

    @Test
    void approvingTheQueueWithoutAKindApprovesEveryKind() throws Exception {
        mockMvc.perform(postWithCsrf("/api/admin/articles/devApproveAll").cookie(signIn(ADMIN)))
               .andExpect(status().isNoContent());

        verify(articleReviewService).devApproveAll(null);
    }

    @Test
    void anUnknownKindIsA400() throws Exception {
        mockMvc.perform(postWithCsrf("/api/admin/articles/devApproveAll?kind=draft").cookie(signIn(ADMIN)))
               .andExpect(status().isBadRequest());

        verifyNoInteractions(articleReviewService);
    }

    @Test
    void aVendorCannotPushTestArticles() throws Exception {
        mockMvc.perform(multipartWithCsrf(HttpMethod.POST, "/api/admin/articles/devRequest?vendorIndex=0")
                                .part(payloadPart(REQUEST))
                                .cookie(signIn(VENDOR)))
               .andExpect(status().isForbidden());

        verifyNoInteractions(articleReviewService);
    }

    @Test
    void aPushedSubmissionReachesTheServiceWithItsVendorAndImages() throws Exception {
        mockMvc.perform(multipartWithCsrf(HttpMethod.POST, "/api/admin/articles/devSubmitAs?vendorEmail=" + VENDOR)
                                .part(payloadPart(SUBMISSION))
                                .file(image("featured-image", "cover.png"))
                                .file(image("additional-image-0", "first.png"))
                                .cookie(signIn(ADMIN)))
               .andExpect(status().isCreated());

        verify(articleReviewService).devSubmitArticleAs(
                eq(VENDOR), argThat(payload -> payload.quotedTotal() == 89),
                argThat(file -> "cover.png".equals(file.getOriginalFilename())),
                argThat(files -> files.size() == 1));
    }

    @Test
    void aPushedRequestReachesTheServiceWithItsVendorIndex() throws Exception {
        mockMvc.perform(multipartWithCsrf(HttpMethod.POST, "/api/admin/articles/devRequest?vendorIndex=12")
                                .part(payloadPart(REQUEST))
                                .cookie(signIn(ADMIN)))
               .andExpect(status().isCreated());

        verify(articleReviewService).devRequestArticleAt(eq(12), any(), eq(null));
    }

    private static final String SUBMISSION = """
            {"headline":"Acme ships a faster writer","topics":["AI News"],"body":"Words go here.","sources":[],
             "options":{"permanentLinks":false,"unlabelled":false},
             "consents":{"accurate":true,"terms":true},"quotedTotal":89}
            """;

    private static final String REQUEST = """
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

    private static MockMultipartFile image(String part, String name) {
        return new MockMultipartFile(part, name, MediaType.IMAGE_PNG_VALUE, new byte[]{1, 2, 3});
    }
}
