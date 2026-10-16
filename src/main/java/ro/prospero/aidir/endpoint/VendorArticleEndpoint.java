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
import ro.prospero.aidir.data.ArticleOrderDTO;
import ro.prospero.aidir.data.ArticleRequestPayload;
import ro.prospero.aidir.data.ArticleSubmissionPayload;
import ro.prospero.aidir.security.AccountPrincipal;
import ro.prospero.aidir.service.ArticleOrderService;

import java.util.List;

import static ro.prospero.aidir.endpoint.VendorPrincipals.vendorId;

/**
 * The signed-in vendor's sponsored article orders. {@code submissions} are articles the vendor wrote,
 * {@code requests} briefs for our editors; both are multipart, laid out as {@link ArticleRequestReader} reads
 * them, and both answer with the order as placed, its reference and price included.
 */
@RestController
@RequestMapping(path = "api/vendor/articles", produces = MediaType.APPLICATION_JSON_VALUE)
public class VendorArticleEndpoint {
    private final ArticleOrderService articleOrderService;
    private final ArticleRequestReader requestReader;

    public VendorArticleEndpoint(ArticleOrderService articleOrderService, ArticleRequestReader requestReader) {
        this.articleOrderService = articleOrderService;
        this.requestReader = requestReader;
    }

    @GetMapping
    public List<ArticleOrderDTO> list(@AuthenticationPrincipal AccountPrincipal principal) {
        return articleOrderService.list(vendorId(principal));
    }

    @GetMapping("{id}")
    public ArticleOrderDTO get(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable long id) {
        return articleOrderService.get(vendorId(principal), id);
    }

    @PostMapping(path = "submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ArticleOrderDTO submit(@AuthenticationPrincipal AccountPrincipal principal,
                                  @RequestPart("payload") String payloadJson,
                                  MultipartRequest request) throws JsonProcessingException {
        long accountId = vendorId(principal);
        return articleOrderService.submitArticle(accountId,
                                                 requestReader.payload(payloadJson, ArticleSubmissionPayload.class),
                                                 requestReader.featuredImage(request),
                                                 requestReader.additionalImages(request));
    }

    @PostMapping(path = "requests", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ArticleOrderDTO request(@AuthenticationPrincipal AccountPrincipal principal,
                                   @RequestPart("payload") String payloadJson,
                                   MultipartRequest request) throws JsonProcessingException {
        long accountId = vendorId(principal);
        return articleOrderService.requestArticle(accountId,
                                                  requestReader.payload(payloadJson, ArticleRequestPayload.class),
                                                  requestReader.featuredImage(request));
    }
}
