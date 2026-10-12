package ro.prospero.aidir.endpoint;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.prospero.aidir.data.PublicDealDTO;
import ro.prospero.aidir.service.PublicDealService;

import java.util.List;

/** The public deals directory. Open to anyone. */
@RestController
@RequestMapping(path = "api/deals", produces = MediaType.APPLICATION_JSON_VALUE)
public class DealEndpoint {
    private final PublicDealService publicDealService;

    public DealEndpoint(PublicDealService publicDealService) {
        this.publicDealService = publicDealService;
    }

    @GetMapping
    public List<PublicDealDTO> listRunning() {
        return publicDealService.listRunning();
    }
}
