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
import ro.prospero.aidir.data.ArticleSubmissionPayload;
import ro.prospero.aidir.data.SubmittedArticleDTO;
import ro.prospero.aidir.security.AccountPrincipal;
import ro.prospero.aidir.service.SubmittedArticleService;

import java.util.List;

import static ro.prospero.aidir.endpoint.VendorPrincipals.vendorId;

/**
 * The articles the signed-in vendor wrote and sent us to publish. A submission is multipart, laid out as
 * {@link ArticleMultipartReader} reads it, and answers with the article as submitted, its reference and price
 * included.
 */
@RestController
@RequestMapping(path = "api/vendor/articles", produces = MediaType.APPLICATION_JSON_VALUE)
public class VendorArticleEndpoint {
    private final SubmittedArticleService articleService;
    private final ArticleMultipartReader multipartReader;

    public VendorArticleEndpoint(SubmittedArticleService articleService, ArticleMultipartReader multipartReader) {
        this.articleService = articleService;
        this.multipartReader = multipartReader;
    }

    @GetMapping
    public List<SubmittedArticleDTO> list(@AuthenticationPrincipal AccountPrincipal principal) {
        return articleService.list(vendorId(principal));
    }

    @GetMapping("{id}")
    public SubmittedArticleDTO get(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable long id) {
        return articleService.get(vendorId(principal), id);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public SubmittedArticleDTO submit(@AuthenticationPrincipal AccountPrincipal principal,
                                      @RequestPart("payload") String payloadJson,
                                      MultipartRequest request) throws JsonProcessingException {
        long accountId = vendorId(principal);
        return articleService.submit(accountId,
                                     multipartReader.payload(payloadJson, ArticleSubmissionPayload.class, "article"),
                                     multipartReader.featuredImage(request),
                                     multipartReader.additionalImages(request));
    }
}
