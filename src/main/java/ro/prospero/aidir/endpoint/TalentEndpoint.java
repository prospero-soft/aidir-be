package ro.prospero.aidir.endpoint;

import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import ro.prospero.aidir.data.TalentBrowseQuery;
import ro.prospero.aidir.data.TalentLocationScope;
import ro.prospero.aidir.data.TalentPage;
import ro.prospero.aidir.data.TalentSort;
import ro.prospero.aidir.service.TalentBrowseService;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("api/talent")
public class TalentEndpoint {
    private TalentBrowseService talentBrowseService;

    /**
     * The talent directory. Every parameter is optional, and with none of them this is the whole directory,
     * newest profiles first: browsing is the default, searching and filtering are what narrow it.
     *
     * <p>{@code keyword}, {@code location}, {@code skill} and {@code language} repeat rather than taking a
     * delimited list - locations are "City, Country" and skills carry punctuation of their own, so a
     * delimiter would have to be escaped out of the values.
     *
     * <p>Only profiles are listed. An account on the free tier has no {@code talent_profile} row and so
     * never appears here, which is the intended behaviour rather than an omission.
     */
    @GetMapping("browse")
    @ResponseBody
    public TalentPage browse(@RequestParam(required = false) String query,
                             @RequestParam(required = false) String jobTitle,
                             @RequestParam(required = false) List<String> keyword,
                             @RequestParam(required = false) List<String> location,
                             @RequestParam(required = false) TalentLocationScope locationScope,
                             @RequestParam(required = false) String workplace,
                             @RequestParam(required = false) String employmentType,
                             @RequestParam(required = false) List<String> skill,
                             @RequestParam(required = false) List<String> language,
                             @RequestParam(required = false) TalentSort sort,
                             @RequestParam(required = false) Integer page,
                             @RequestParam(required = false) Integer size) {
        return talentBrowseService.browse(TalentBrowseQuery.of(query,
                                                              jobTitle,
                                                              keyword,
                                                              location,
                                                              locationScope,
                                                              workplace,
                                                              employmentType,
                                                              skill,
                                                              language,
                                                              sort,
                                                              page,
                                                              size));
    }
}
