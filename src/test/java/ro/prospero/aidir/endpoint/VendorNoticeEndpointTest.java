package ro.prospero.aidir.endpoint;

import org.junit.jupiter.api.Test;
import ro.prospero.aidir.data.VendorNoticeDTO;
import ro.prospero.aidir.data.VendorNoticeType;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The vendor's notices as the web layer sees them: who may read them, and how a notice is written out. */
class VendorNoticeEndpointTest extends EndpointSecurityTestBase {
    private static final long VENDOR_ID = 1L;

    @Test
    void withoutASessionIsA401() throws Exception {
        mockMvc.perform(get("/api/vendor/notices"))
               .andExpect(status().isUnauthorized());

        verifyNoInteractions(vendorNoticeService);
    }

    @Test
    void aTalentAccountIsA403() throws Exception {
        mockMvc.perform(get("/api/vendor/notices").cookie(signIn(TALENT)))
               .andExpect(status().isForbidden());

        verifyNoInteractions(vendorNoticeService);
    }

    @Test
    void aVendorGetsTheirNoticesByType() throws Exception {
        when(vendorNoticeService.notices(VENDOR_ID)).thenReturn(List.of(
                new VendorNoticeDTO(VendorNoticeType.ARTICLE_CHANGES_REQUESTED, 42L, "Acme ships a faster writer",
                                    OffsetDateTime.parse("2026-10-20T09:00:00Z"))));

        mockMvc.perform(get("/api/vendor/notices").cookie(signIn(VENDOR)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$[0].type").value("article_changes_requested"))
               .andExpect(jsonPath("$[0].subjectId").value(42))
               .andExpect(jsonPath("$[0].subject").value("Acme ships a faster writer"));
    }
}
