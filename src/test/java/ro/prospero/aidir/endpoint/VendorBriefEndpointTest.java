package ro.prospero.aidir.endpoint;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import ro.prospero.aidir.data.ArticleBriefDTO;
import ro.prospero.aidir.data.ArticleBriefPayload;
import ro.prospero.aidir.data.ArticleBriefStatus;
import ro.prospero.aidir.data.ArticleQuoteDTO;
import ro.prospero.aidir.data.WritingTier;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;

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
 * The vendor's brief routes as the web layer sees them: who may call them, what reaches the service, and what
 * a bad payload answers. What the service does with them is not tested here.
 */
class VendorBriefEndpointTest extends EndpointSecurityTestBase {
    private static final long VENDOR_ID = 1L;

    private static final String BRIEF = """
            {"headline":"Acme ships a faster writer","brief":"Cover the launch.","topics":["Enterprise"],
             "audience":null,"keyMessages":["It is fast","It is cheap","It is here"],"sources":[],
             "preferredDate":null,
             "options":{"writing":"standard","links":2,"permanentLinks":false,"unlabelled":false},
             "consents":{"accurate":true,"terms":true},"quotedTotal":251}
            """;

    private static MockMultipartHttpServletRequestBuilder post(String url, String json) {
        MockPart part = new MockPart("payload", json.getBytes(StandardCharsets.UTF_8));
        part.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        MockMultipartHttpServletRequestBuilder builder = multipartWithCsrf(HttpMethod.POST, url);
        builder.part(part);
        return builder;
    }

    private static ArticleBriefDTO brief(long id) {
        return new ArticleBriefDTO(id, "AIDB-2026-%04d".formatted(id), ArticleBriefStatus.RECEIVED,
                                   "Acme ships a faster writer", "Cover the launch.", null,
                                   List.of("It is fast", "It is cheap", "It is here"), null, null,
                                   List.of("Enterprise"), List.of(),
                                   new ArticleBriefDTO.Options(WritingTier.STANDARD, 2, false, false),
                                   new ArticleQuoteDTO(List.of(), 251, 0, 251), null,
                                   OffsetDateTime.parse("2026-10-16T10:00:00Z"), null, null);
    }

    @Test
    void theListWithoutASessionIsA401() throws Exception {
        mockMvc.perform(get("/api/vendor/briefs"))
               .andExpect(status().isUnauthorized());

        verifyNoInteractions(articleBriefService);
    }

    @Test
    void aTalentAccountIsA403() throws Exception {
        mockMvc.perform(post("/api/vendor/briefs", BRIEF).cookie(signIn(TALENT)))
               .andExpect(status().isForbidden());

        verifyNoInteractions(articleBriefService);
    }

    @Test
    void aBriefWithoutAnImageHandsTheServiceNone() throws Exception {
        when(articleBriefService.place(anyLong(), any(), any())).thenReturn(brief(7L));

        mockMvc.perform(post("/api/vendor/briefs", BRIEF).cookie(signIn(VENDOR)))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.id").value(7))
               .andExpect(jsonPath("$.reference").value("AIDB-2026-0007"))
               .andExpect(jsonPath("$.status").value("received"));

        verify(articleBriefService).place(
                eq(VENDOR_ID),
                argThat((ArticleBriefPayload payload) -> payload.options().writing() == WritingTier.STANDARD
                                                         && payload.keyMessages().size() == 3),
                isNull());
    }

    @Test
    void tooFewKeyMessagesIsA400() throws Exception {
        String twoMessages = BRIEF.replace(",\"It is here\"", "");

        mockMvc.perform(post("/api/vendor/briefs", twoMessages).cookie(signIn(VENDOR)))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.detail").value("Validating the brief failed."))
               .andExpect(jsonPath("$.constraintViolations[0]").value(
                       "keyMessages: size must be between 3 and 5"));

        verifyNoInteractions(articleBriefService);
    }

    @Test
    void anUnknownWritingTierIsA400() throws Exception {
        String unknown = BRIEF.replace("\"standard\"", "\"epic\"");

        mockMvc.perform(post("/api/vendor/briefs", unknown).cookie(signIn(VENDOR)))
               .andExpect(status().isBadRequest());

        verifyNoInteractions(articleBriefService);
    }
}
