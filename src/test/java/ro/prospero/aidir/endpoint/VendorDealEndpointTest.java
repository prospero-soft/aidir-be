package ro.prospero.aidir.endpoint;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import ro.prospero.aidir.data.DealDTO;
import ro.prospero.aidir.data.DealPayload;
import ro.prospero.aidir.data.DealStatus;
import ro.prospero.aidir.data.OfferType;
import ro.prospero.aidir.data.Placement;
import ro.prospero.aidir.data.VendorCompanyDTO;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The vendor's deal routes as the web layer sees them: who may call them, how the multipart form arrives
 * at the service, and what a bad payload answers. What the service does with it is not tested here.
 */
class VendorDealEndpointTest extends EndpointSecurityTestBase {
    private static final long VENDOR_ID = 1L;

    private static final LocalDate START = LocalDate.now().plusDays(20);

    private static final String PAYLOAD = """
            {"placements":["sidebar","top-category"],"sponsored":true,"sponsoredLabel":"Partner",
             "productName":"Acme Writer","category":"Content Creation","logoUrl":null,
             "perPlacement":{"top-category":{
               "schedule":{"durationDays":7,"startDate":"%s"},
               "info":{},
               "offer":{"offerType":"percentage","currency":"EUR","originalPrice":100,"dealPrice":60,
                        "discountMonths":12,"buttonText":"Get Started",
                        "destinationUrl":"https://acme.example/deal"}}}}
            """.formatted(START);

    private static MockPart payloadPart(String json) {
        MockPart part = new MockPart("payload", json.getBytes(StandardCharsets.UTF_8));
        part.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return part;
    }

    private static MockMultipartFile logo() {
        return new MockMultipartFile("logo", "logo.png", MediaType.IMAGE_PNG_VALUE, new byte[]{1, 2, 3});
    }

    private ResultActions save(Cookie session, HttpMethod method, String url, String json) throws Exception {
        return mockMvc.perform(multipartWithCsrf(method, url).part(payloadPart(json))
                                                             .file(logo())
                                                             .cookie(session));
    }

    private static DealDTO deal(long id, DealStatus status) {
        return new DealDTO(id, status, List.of(Placement.TOP_CATEGORY), false, null, null, null, null, null, null,
                           null, null, null, Map.of(), null,
                           OffsetDateTime.parse("2026-10-01T10:00:00Z"), OffsetDateTime.parse("2026-10-01T10:00:00Z"),
                           null, null);
    }

    @Test
    void theListWithoutASessionIsA401() throws Exception {
        mockMvc.perform(get("/api/vendor/deals"))
               .andExpect(status().isUnauthorized());

        verifyNoInteractions(dealService);
    }

    @Test
    void aTalentAccountIsA403() throws Exception {
        mockMvc.perform(get("/api/vendor/deals").cookie(signIn(TALENT)))
               .andExpect(status().isForbidden())
               .andExpect(jsonPath("$.detail").value("Your account is not allowed to do that."));

        verifyNoInteractions(dealService);
    }

    @Test
    void anAdminIsA403Too() throws Exception {
        mockMvc.perform(get("/api/vendor/deals").cookie(signIn(ADMIN)))
               .andExpect(status().isForbidden());
    }

    @Test
    void aSaveWithoutTheCsrfHeaderIsA403() throws Exception {
        mockMvc.perform(multipart("/api/vendor/deals")
                                .part(payloadPart(PAYLOAD))
                                .cookie(signIn(VENDOR)))
               .andExpect(status().isForbidden())
               .andExpect(jsonPath("$.detail").value("The CSRF token is missing or does not match."));

        verifyNoInteractions(dealService);
    }

    @Test
    void creatingADraftHandsThePayloadAndLogosToTheService() throws Exception {
        when(dealService.create(anyLong(), any(), any())).thenReturn(deal(42L, DealStatus.DRAFT));

        save(signIn(VENDOR), HttpMethod.POST, "/api/vendor/deals", PAYLOAD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.status").value("draft"))
                .andExpect(jsonPath("$.placements[0]").value("top-category"));

        verify(dealService).create(
                eq(VENDOR_ID),
                argThat((DealPayload payload) -> {
                    DealPayload.PlacementContent content = payload.perPlacement().get("top-category");
                    return payload.placements().equals(List.of(Placement.SIDEBAR, Placement.TOP_CATEGORY))
                           && payload.sponsored()
                           && payload.productName().equals("Acme Writer")
                           && content.schedule().startDate().equals(START)
                           && content.offer().offerType() == OfferType.PERCENTAGE;
                }),
                argThat((MultipartFile logo) -> logo.getOriginalFilename().equals("logo.png")));
    }

    @Test
    void updatingReachesTheSessionsVendorAndTheDealInThePath() throws Exception {
        when(dealService.update(anyLong(), anyLong(), any(), any())).thenReturn(deal(7L, DealStatus.DRAFT));

        save(signIn(VENDOR), HttpMethod.PUT, "/api/vendor/deals/7", PAYLOAD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));

        verify(dealService).update(eq(VENDOR_ID), eq(7L), any(), argThat((MultipartFile logo) -> logo != null));
    }

    @Test
    void anInvalidPayloadIsA400NamingTheFieldAndSavesNothing() throws Exception {
        String badButton = PAYLOAD.replace("\"Get Started\"", "\"Click me\"");

        save(signIn(VENDOR), HttpMethod.POST, "/api/vendor/deals", badButton)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Validating the deal failed."))
                .andExpect(jsonPath("$.constraintViolations[0]").value(
                        startsWith("perPlacement[top-category].offer.buttonText: ")));

        verifyNoInteractions(dealService);
    }

    @Test
    void aStartDateInThePastIsA400OnCreate() throws Exception {
        String yesterday = PAYLOAD.replace(START.toString(), LocalDate.now().minusDays(1).toString());

        save(signIn(VENDOR), HttpMethod.POST, "/api/vendor/deals", yesterday)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.constraintViolations[0]").value(
                        "perPlacement[top-category].schedule.startDate: The start date can't be in the past"));

        verifyNoInteractions(dealService);
    }

    @Test
    void aStartDateInThePastIsA400OnUpdate() throws Exception {
        String lastYear = PAYLOAD.replace(START.toString(), LocalDate.now().minusYears(1).toString());

        save(signIn(VENDOR), HttpMethod.PUT, "/api/vendor/deals/7", lastYear)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.constraintViolations[0]").value(
                        startsWith("perPlacement[top-category].schedule.startDate: ")));

        verifyNoInteractions(dealService);
    }

    @Test
    void aStartDateOfTodayIsAccepted() throws Exception {
        when(dealService.create(anyLong(), any(), any())).thenReturn(deal(42L, DealStatus.DRAFT));

        save(signIn(VENDOR), HttpMethod.POST, "/api/vendor/deals", PAYLOAD.replace(START.toString(),
                                                                               LocalDate.now().toString()))
                .andExpect(status().isCreated());
    }

    @Test
    void anUnknownPlacementIsA400() throws Exception {
        save(signIn(VENDOR), HttpMethod.POST, "/api/vendor/deals",
             PAYLOAD.replace("\"sidebar\"", "\"billboard\""))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(dealService);
    }

    @Test
    void submittingAsksTheServiceForTheSessionsDeal() throws Exception {
        when(dealService.submit(anyLong(), anyLong())).thenReturn(deal(7L, DealStatus.PENDING));

        mockMvc.perform(postWithCsrf("/api/vendor/deals/7/submit").cookie(signIn(VENDOR)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.status").value("pending"));

        verify(dealService).submit(VENDOR_ID, 7L);
    }

    @Test
    void changingADealUnderReviewIsA409() throws Exception {
        when(dealService.update(anyLong(), anyLong(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT,
                                                       "This deal is waiting for review and can't be changed."));

        save(signIn(VENDOR), HttpMethod.PUT, "/api/vendor/deals/7", PAYLOAD)
                .andExpect(status().isConflict());
    }

    @Test
    void deletingAnswersWithNoContent() throws Exception {
        mockMvc.perform(delete("/api/vendor/deals/7")
                                .cookie(new Cookie(CSRF_COOKIE, CSRF_TOKEN))
                                .header(CSRF_HEADER, CSRF_TOKEN)
                                .cookie(signIn(VENDOR)))
               .andExpect(status().isNoContent());

        verify(dealService).delete(VENDOR_ID, 7L);
    }

    @Test
    void theCompanyIsTheSessionsVendors() throws Exception {
        when(vendorCompanyService.get(VENDOR_ID)).thenReturn(
                new VendorCompanyDTO(VENDOR_ID, "Acme Tools GmbH", "https://acme.example",
                                                           VENDOR, "premium", "active"));

        mockMvc.perform(get("/api/vendor/company").cookie(signIn(VENDOR)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.clientId").value(VENDOR_ID))
               .andExpect(jsonPath("$.companyName").value("Acme Tools GmbH"));

        mockMvc.perform(get("/api/vendor/company").cookie(signIn(TALENT)))
               .andExpect(status().isForbidden());
    }
}
