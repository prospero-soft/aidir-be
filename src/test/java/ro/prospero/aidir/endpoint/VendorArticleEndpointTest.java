package ro.prospero.aidir.endpoint;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import ro.prospero.aidir.data.ArticleOrderDTO;
import ro.prospero.aidir.data.ArticleOrderKind;
import ro.prospero.aidir.data.ArticleOrderStatus;
import ro.prospero.aidir.data.ArticleQuoteDTO;
import ro.prospero.aidir.data.ArticleRequestPayload;
import ro.prospero.aidir.data.ArticleSubmissionPayload;
import ro.prospero.aidir.data.WritingTier;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The vendor's article routes as the web layer sees them: who may call them, how the multipart parts reach
 * the service, and what a bad payload answers. What the service does with them is not tested here.
 */
class VendorArticleEndpointTest extends EndpointSecurityTestBase {
    private static final long VENDOR_ID = 1L;

    private static final String SUBMISSION = """
            {"headline":"Acme ships a faster writer","topics":["AI News"],"body":"Words go here.",
             "sources":["https://acme.example/press"],
             "options":{"permanentLinks":true,"unlabelled":false},
             "consents":{"accurate":true,"terms":true},"quotedTotal":124}
            """;

    private static final String REQUEST = """
            {"headline":"Acme ships a faster writer","brief":"Cover the launch.","topics":["Enterprise"],
             "audience":null,"keyMessages":["It is fast","It is cheap","It is here"],"sources":[],
             "preferredDate":null,
             "options":{"writing":"standard","links":2,"permanentLinks":false,"unlabelled":false},
             "consents":{"accurate":true,"terms":true},"quotedTotal":251}
            """;

    private static MockPart payloadPart(String json) {
        MockPart part = new MockPart("payload", json.getBytes(StandardCharsets.UTF_8));
        part.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return part;
    }

    private static MockMultipartFile image(String part, String name) {
        return new MockMultipartFile(part, name, MediaType.IMAGE_PNG_VALUE, new byte[]{1, 2, 3});
    }

    private static MockMultipartHttpServletRequestBuilder post(String url, String json) {
        MockMultipartHttpServletRequestBuilder builder = multipartWithCsrf(HttpMethod.POST, url);
        builder.part(payloadPart(json));
        return builder;
    }

    private static ArticleOrderDTO order(long id, ArticleOrderKind kind) {
        return new ArticleOrderDTO(id, "AIDP-2026-%04d".formatted(id), kind, ArticleOrderStatus.PENDING,
                                   "Acme ships a faster writer", List.of("AI News"), null, List.of(), null, null,
                                   new ArticleOrderDTO.Options(1, false, false),
                                   new ArticleQuoteDTO(List.of(), 79, 0, 79), null,
                                   OffsetDateTime.parse("2026-10-16T10:00:00Z"), null, null);
    }

    @Test
    void theListWithoutASessionIsA401() throws Exception {
        mockMvc.perform(get("/api/vendor/articles"))
               .andExpect(status().isUnauthorized());

        verifyNoInteractions(articleOrderService);
    }

    @Test
    void aTalentAccountIsA403() throws Exception {
        mockMvc.perform(post("/api/vendor/articles/submissions", SUBMISSION).cookie(signIn(TALENT)))
               .andExpect(status().isForbidden());

        verifyNoInteractions(articleOrderService);
    }

    @Test
    void aSubmissionHandsThePayloadAndImagesInOrderToTheService() throws Exception {
        MockMultipartFile featured = image("featured-image", "cover.png");
        MockMultipartFile second = image("additional-image-1", "second.png");
        MockMultipartFile first = image("additional-image-0", "first.png");
        when(articleOrderService.submitArticle(anyLong(), any(), any(), any()))
                .thenReturn(order(42L, ArticleOrderKind.SUBMITTED));

        mockMvc.perform(post("/api/vendor/articles/submissions", SUBMISSION).file(featured)
                                                                             .file(second)
                                                                             .file(first)
                                                                             .cookie(signIn(VENDOR)))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.reference").value("AIDP-2026-0042"))
               .andExpect(jsonPath("$.kind").value("submitted"));

        verify(articleOrderService).submitArticle(
                eq(VENDOR_ID),
                argThat((ArticleSubmissionPayload payload) -> payload.options().permanentLinks()
                                                              && payload.quotedTotal() == 124),
                argThat(file -> "cover.png".equals(file.getOriginalFilename())),
                argThat(files -> files.stream().map(f -> f.getOriginalFilename()).toList()
                                      .equals(List.of("first.png", "second.png"))));
    }

    @Test
    void aRequestWithoutAnImageHandsTheServiceNone() throws Exception {
        when(articleOrderService.requestArticle(anyLong(), any(), any()))
                .thenReturn(order(7L, ArticleOrderKind.EDITORIAL));

        mockMvc.perform(post("/api/vendor/articles/requests", REQUEST).cookie(signIn(VENDOR)))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.id").value(7));

        verify(articleOrderService).requestArticle(
                eq(VENDOR_ID),
                argThat((ArticleRequestPayload payload) -> payload.options().writing() == WritingTier.STANDARD
                                                           && payload.keyMessages().size() == 3),
                isNull());
    }

    @Test
    void aSubmissionWithoutConsentIsA400AndPlacesNothing() throws Exception {
        String noTerms = SUBMISSION.replace("\"terms\":true", "\"terms\":false");

        mockMvc.perform(post("/api/vendor/articles/submissions", noTerms).cookie(signIn(VENDOR)))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.detail").value("Validating the article order failed."))
               .andExpect(jsonPath("$.constraintViolations").value(
                       hasItem("consents.terms: Accept the terms and the publication rules")));

        verifyNoInteractions(articleOrderService);
    }

    @Test
    void tooFewKeyMessagesIsA400() throws Exception {
        String twoMessages = REQUEST.replace(",\"It is here\"", "");

        mockMvc.perform(post("/api/vendor/articles/requests", twoMessages).cookie(signIn(VENDOR)))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.constraintViolations[0]").value(
                       "keyMessages: size must be between 3 and 5"));

        verifyNoInteractions(articleOrderService);
    }

    @Test
    void anUnknownWritingTierIsA400() throws Exception {
        String unknown = REQUEST.replace("\"standard\"", "\"epic\"");

        mockMvc.perform(post("/api/vendor/articles/requests", unknown).cookie(signIn(VENDOR)))
               .andExpect(status().isBadRequest());

        verifyNoInteractions(articleOrderService);
    }
}
