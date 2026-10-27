package ro.prospero.aidir.endpoint;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import ro.prospero.aidir.data.ArticleQuoteDTO;
import ro.prospero.aidir.data.ArticleRevisionPayload;
import ro.prospero.aidir.data.ArticleSubmissionPayload;
import ro.prospero.aidir.data.SubmittedArticleDTO;
import ro.prospero.aidir.data.SubmittedArticleStatus;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Arrays;
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
 * The vendor's submitted article routes as the web layer sees them: who may call them, how the multipart parts
 * reach the service, and what a bad payload answers. What the service does with them is not tested here.
 */
class VendorArticleEndpointTest extends EndpointSecurityTestBase {
    private static final long VENDOR_ID = 1L;

    private static final String SUBMISSION = """
            {"headline":"Acme ships a faster writer","topics":["AI News"],
             "body":[{"kind":"paragraph","content":[{"text":"Words go here.","href":null,"bold":false,"italic":false}]},
                     {"kind":"image","image":0,"alt":""},{"kind":"image","image":1,"alt":""}],
             "sources":["https://acme.example/press"],
             "options":{"permanentLinks":true,"unlabelled":false},
             "consents":{"accurate":true,"terms":true},"quotedTotal":124}
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

    private static SubmittedArticleDTO article(long id) {
        return new SubmittedArticleDTO(id, "AIDA-2026-%04d".formatted(id), SubmittedArticleStatus.PENDING_REVIEW,
                                       "Acme ships a faster writer", List.of("AI News"), null, List.of(), List.of(),
                                       List.of(), new SubmittedArticleDTO.Options(1, 0, false, false),
                                       new ArticleQuoteDTO(List.of(), 79, 0, 79), null, List.of(),
                                       OffsetDateTime.parse("2026-10-16T10:00:00Z"),
                                       OffsetDateTime.parse("2026-10-16T10:00:00Z"), null, null, null);
    }

    private static final String REVISION = """
            {"headline":"Acme ships a faster writer","topics":["AI News"],
             "body":[{"kind":"paragraph","content":[{"text":"Words go here.","href":null,"bold":false,"italic":false}]},
                     {"kind":"image","image":0,"alt":""},{"kind":"image","image":1,"alt":""}],
             "sources":[],
             "consents":{"accurate":true,"terms":true},"keptImages":[1,null]}
            """;

    private static MockMultipartHttpServletRequestBuilder put(String url, String json) {
        MockMultipartHttpServletRequestBuilder builder = multipartWithCsrf(HttpMethod.PUT, url);
        builder.part(payloadPart(json));
        return builder;
    }

    @Test
    void theListWithoutASessionIsA401() throws Exception {
        mockMvc.perform(get("/api/vendor/articles"))
               .andExpect(status().isUnauthorized());

        verifyNoInteractions(submittedArticleService);
    }

    @Test
    void aTalentAccountIsA403() throws Exception {
        mockMvc.perform(post("/api/vendor/articles", SUBMISSION).cookie(signIn(TALENT)))
               .andExpect(status().isForbidden());

        verifyNoInteractions(submittedArticleService);
    }

    @Test
    void aSubmissionHandsThePayloadAndImagesInOrderToTheService() throws Exception {
        MockMultipartFile featured = image("featured-image", "cover.png");
        MockMultipartFile second = image("additional-image-1", "second.png");
        MockMultipartFile first = image("additional-image-0", "first.png");
        when(submittedArticleService.submit(anyLong(), any(), any(), any())).thenReturn(article(42L));

        mockMvc.perform(post("/api/vendor/articles", SUBMISSION).file(featured)
                                                                 .file(second)
                                                                 .file(first)
                                                                 .cookie(signIn(VENDOR)))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.reference").value("AIDA-2026-0042"))
               .andExpect(jsonPath("$.status").value("pending_review"));

        verify(submittedArticleService).submit(
                eq(VENDOR_ID),
                argThat((ArticleSubmissionPayload payload) -> payload.options().permanentLinks()
                                                              && payload.quotedTotal() == 124
                                                              && payload.body().size() == 3),
                argThat(file -> "cover.png".equals(file.getOriginalFilename())),
                argThat(files -> files.stream().map(f -> f.getOriginalFilename()).toList()
                                      .equals(List.of("first.png", "second.png"))));
    }

    @Test
    void aSubmissionWithoutConsentIsA400AndSubmitsNothing() throws Exception {
        String noTerms = SUBMISSION.replace("\"terms\":true", "\"terms\":false");

        mockMvc.perform(post("/api/vendor/articles", noTerms).cookie(signIn(VENDOR)))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.detail").value("Validating the article failed."))
               .andExpect(jsonPath("$.constraintViolations").value(
                       hasItem("consents.terms: Accept the terms and the publication rules")));

        verifyNoInteractions(submittedArticleService);
    }

    @Test
    void aBlankHeadingInTheBodyIsA400() throws Exception {
        String blankHeading = SUBMISSION.replace("{\"kind\":\"image\",\"image\":1,\"alt\":\"\"}",
                                                 "{\"kind\":\"heading\",\"text\":\" \"}");

        mockMvc.perform(post("/api/vendor/articles", blankHeading).cookie(signIn(VENDOR)))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.constraintViolations").value(hasItem("body[2].text: must not be blank")));

        verifyNoInteractions(submittedArticleService);
    }

    @Test
    void aRevisionWithoutASessionIsA401() throws Exception {
        mockMvc.perform(put("/api/vendor/articles/42", REVISION))
               .andExpect(status().isUnauthorized());

        verifyNoInteractions(submittedArticleService);
    }

    @Test
    void aRevisionFromATalentAccountIsA403() throws Exception {
        mockMvc.perform(put("/api/vendor/articles/42", REVISION).cookie(signIn(TALENT)))
               .andExpect(status().isForbidden());

        verifyNoInteractions(submittedArticleService);
    }

    @Test
    void aRevisionHandsThePayloadAndNewImagesToTheServiceAndKeepsTheCoverWhenNoneIsSent() throws Exception {
        MockMultipartFile replacement = image("additional-image-1", "replacement.png");
        when(submittedArticleService.resubmit(anyLong(), anyLong(), any(), any(), any())).thenReturn(article(42L));

        mockMvc.perform(put("/api/vendor/articles/42", REVISION).file(replacement).cookie(signIn(VENDOR)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.reference").value("AIDA-2026-0042"));

        verify(submittedArticleService).resubmit(
                eq(VENDOR_ID),
                eq(42L),
                argThat((ArticleRevisionPayload payload) -> payload.keptImages().equals(Arrays.asList(1, null))),
                isNull(),
                argThat(files -> files.stream().map(f -> f.getOriginalFilename()).toList()
                                      .equals(List.of("replacement.png"))));
    }

    @Test
    void aRevisionWithoutConsentIsA400AndChangesNothing() throws Exception {
        String noTerms = REVISION.replace("\"terms\":true", "\"terms\":false");

        mockMvc.perform(put("/api/vendor/articles/42", noTerms).cookie(signIn(VENDOR)))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.constraintViolations").value(
                       hasItem("consents.terms: Accept the terms and the publication rules")));

        verifyNoInteractions(submittedArticleService);
    }
}
