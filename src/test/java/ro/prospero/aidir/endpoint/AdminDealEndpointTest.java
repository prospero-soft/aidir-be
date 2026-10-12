package ro.prospero.aidir.endpoint;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Deal moderation and the public directory, as far as who may reach them. */
class AdminDealEndpointTest extends EndpointSecurityTestBase {

    @Test
    void aVendorCannotSeeTheQueue() throws Exception {
        mockMvc.perform(get("/api/admin/deals/queue").cookie(signIn(VENDOR)))
               .andExpect(status().isForbidden());

        verifyNoInteractions(dealReviewService);
    }

    @Test
    void anAdminSeesTheQueue() throws Exception {
        when(dealReviewService.queue()).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/deals/queue").cookie(signIn(ADMIN)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$").isArray());
    }

    @Test
    void approvingAnswersWithNoContent() throws Exception {
        mockMvc.perform(postWithCsrf("/api/admin/deals/9/approve").cookie(signIn(ADMIN)))
               .andExpect(status().isNoContent());

        verify(dealReviewService).approve(9L);
    }

    @Test
    void aRejectionNeedsAReason() throws Exception {
        mockMvc.perform(postWithCsrf("/api/admin/deals/9/reject").cookie(signIn(ADMIN))
                                                                  .contentType(MediaType.APPLICATION_JSON)
                                                                  .content("{\"reason\":\"  \"}"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.constraintViolations[0]").value("reason: must not be blank"));

        verify(dealReviewService, never()).reject(anyLong(), anyString());
    }

    @Test
    void aRejectionCarriesItsReason() throws Exception {
        mockMvc.perform(postWithCsrf("/api/admin/deals/9/reject").cookie(signIn(ADMIN))
                                                                  .contentType(MediaType.APPLICATION_JSON)
                                                                  .content("{\"reason\":\"The logo is blurry.\"}"))
               .andExpect(status().isNoContent());

        verify(dealReviewService).reject(9L, "The logo is blurry.");
    }

    @Test
    void theDirectoryIsOpenToAnyone() throws Exception {
        when(publicDealService.listRunning()).thenReturn(List.of());

        mockMvc.perform(get("/api/deals"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$").isArray());
    }
}
