package ro.prospero.aidir.endpoint;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;

import java.nio.charset.StandardCharsets;
import java.util.List;

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

/**
 * The compliance review of submitted articles and its development helpers, as far as who may reach them and
 * what they carry.
 */
class AdminArticleEndpointTest extends EndpointSecurityTestBase {

    private static final String SUBMISSION = """
            {"headline":"Acme ships a faster writer","topics":["AI News"],
             "body":[{"kind":"paragraph","content":[{"text":"Words go here.","href":null,"bold":false,"italic":false}]}],
             "sources":[],
             "options":{"permanentLinks":false,"unlabelled":false},
             "consents":{"accurate":true,"terms":true},"quotedTotal":89}
            """;

    private static MockPart payloadPart(String json) {
        MockPart part = new MockPart("payload", json.getBytes(StandardCharsets.UTF_8));
        part.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return part;
    }

    private static MockMultipartFile image(String part, String name) {
        return new MockMultipartFile(part, name, MediaType.IMAGE_PNG_VALUE, new byte[]{1, 2, 3});
    }

    @Test
    void aVendorCannotSeeTheQueue() throws Exception {
        mockMvc.perform(get("/api/admin/articles/queue").cookie(signIn(VENDOR)))
               .andExpect(status().isForbidden());

        verifyNoInteractions(submittedArticleReviewService);
    }

    @Test
    void anAdminSeesTheQueue() throws Exception {
        when(submittedArticleReviewService.queue()).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/articles/queue").cookie(signIn(ADMIN)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$").isArray());
    }

    @Test
    void approvingAnswersWithNoContent() throws Exception {
        mockMvc.perform(postWithCsrf("/api/admin/articles/9/approve").cookie(signIn(ADMIN)))
               .andExpect(status().isNoContent());

        verify(submittedArticleReviewService).approve(9L);
    }

    @Test
    void requestingChangesNeedsNotes() throws Exception {
        mockMvc.perform(postWithCsrf("/api/admin/articles/9/requestChanges").cookie(signIn(ADMIN))
                                                                             .contentType(MediaType.APPLICATION_JSON)
                                                                             .content("{\"note\":\"  \"}"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.constraintViolations[0]").value("note: must not be blank"));

        verify(submittedArticleReviewService, never()).requestChanges(anyLong(), anyString());
    }

    @Test
    void requestingChangesCarriesTheNotes() throws Exception {
        mockMvc.perform(postWithCsrf("/api/admin/articles/9/requestChanges")
                                .cookie(signIn(ADMIN))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"note\":\"Label the second link as sponsored.\"}"))
               .andExpect(status().isNoContent());

        verify(submittedArticleReviewService).requestChanges(9L, "Label the second link as sponsored.");
    }

    @Test
    void approvingTheQueueAnswersWithNoContent() throws Exception {
        mockMvc.perform(postWithCsrf("/api/admin/articles/devApproveAll").cookie(signIn(ADMIN)))
               .andExpect(status().isNoContent());

        verify(submittedArticleReviewService).devApproveAll();
    }

    @Test
    void aVendorCannotPushTestArticles() throws Exception {
        mockMvc.perform(multipartWithCsrf(HttpMethod.POST, "/api/admin/articles/devSubmit?vendorIndex=0")
                                .part(payloadPart(SUBMISSION))
                                .cookie(signIn(VENDOR)))
               .andExpect(status().isForbidden());

        verifyNoInteractions(submittedArticleReviewService);
    }

    @Test
    void aPushedArticleReachesTheServiceWithItsVendorAndImages() throws Exception {
        mockMvc.perform(multipartWithCsrf(HttpMethod.POST, "/api/admin/articles/devSubmitAs?vendorEmail=" + VENDOR)
                                .part(payloadPart(SUBMISSION))
                                .file(image("featured-image", "cover.png"))
                                .file(image("additional-image-0", "first.png"))
                                .cookie(signIn(ADMIN)))
               .andExpect(status().isCreated());

        verify(submittedArticleReviewService).devSubmitAs(
                eq(VENDOR), argThat(payload -> payload.quotedTotal() == 89),
                argThat(file -> "cover.png".equals(file.getOriginalFilename())),
                argThat(files -> files.size() == 1));
    }
}
