package ro.prospero.aidir.endpoint;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartRequest;
import ro.prospero.aidir.data.ArticleBriefDTO;
import ro.prospero.aidir.data.ArticleBriefPayload;
import ro.prospero.aidir.security.AccountPrincipal;
import ro.prospero.aidir.service.ArticleBriefService;

import java.util.List;

import static ro.prospero.aidir.endpoint.VendorPrincipals.vendorId;

/**
 * The signed-in vendor's briefs for articles our editors write. Placing one is multipart, laid out as
 * {@link ArticleMultipartReader} reads it, and answers with the brief as placed, its reference and price
 * included.
 */
@RestController
@RequestMapping(path = "api/vendor/briefs", produces = MediaType.APPLICATION_JSON_VALUE)
public class VendorBriefEndpoint {
    private final ArticleBriefService briefService;
    private final ArticleMultipartReader multipartReader;

    public VendorBriefEndpoint(ArticleBriefService briefService, ArticleMultipartReader multipartReader) {
        this.briefService = briefService;
        this.multipartReader = multipartReader;
    }

    @GetMapping
    public List<ArticleBriefDTO> list(@AuthenticationPrincipal AccountPrincipal principal) {
        return briefService.list(vendorId(principal));
    }

    @GetMapping("{id}")
    public ArticleBriefDTO get(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable long id) {
        return briefService.get(vendorId(principal), id);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ArticleBriefDTO place(@AuthenticationPrincipal AccountPrincipal principal,
                                 @RequestPart("payload") String payloadJson,
                                 MultipartRequest request) throws JsonProcessingException {
        long accountId = vendorId(principal);
        return briefService.place(accountId,
                                  multipartReader.payload(payloadJson, ArticleBriefPayload.class, "brief"),
                                  multipartReader.featuredImage(request));
    }
}
