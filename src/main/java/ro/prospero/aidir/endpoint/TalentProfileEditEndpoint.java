package ro.prospero.aidir.endpoint;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.prospero.aidir.data.TalentDetailsDTO;
import ro.prospero.aidir.data.TalentProfileEdit;
import ro.prospero.aidir.data.UserOnboardingPayload;
import ro.prospero.aidir.exception.ApiPayloadValidationException;
import ro.prospero.aidir.security.AccountPrincipal;
import ro.prospero.aidir.service.TalentProfileEditService;

import java.util.Set;

/**
 * The signed-in talent editing their own profile, one section per route. Which profile is never in the
 * request: it is always the session's, so there is no id to tamper with.
 *
 * <p>Only GET is open under {@code /api/talent/**}, so these need a session and a CSRF token outside the
 * {@code dev} profile. Under {@code dev} nothing is enforced, which is why each handler still checks for a
 * principal itself, as {@code AuthEndpoint.me} does.
 */
@RestController
@RequestMapping(path = "api/talent/me",
                consumes = MediaType.APPLICATION_JSON_VALUE,
                produces = MediaType.APPLICATION_JSON_VALUE)
public class TalentProfileEditEndpoint {
    private final TalentProfileEditService editService;
    private final Validator validator;

    public TalentProfileEditEndpoint(TalentProfileEditService editService, Validator validator) {
        this.editService = editService;
        this.validator = validator;
    }

    @PutMapping("general")
    public TalentDetailsDTO general(@AuthenticationPrincipal AccountPrincipal principal,
                                    @RequestBody TalentProfileEdit.General body) {
        return editService.updateGeneral(accountId(principal), validate(body));
    }

    @PutMapping("summary")
    public TalentDetailsDTO summary(@AuthenticationPrincipal AccountPrincipal principal,
                                    @RequestBody TalentProfileEdit.Summary body) {
        return editService.updateSummary(accountId(principal), validate(body));
    }

    @PutMapping("work")
    public TalentDetailsDTO work(@AuthenticationPrincipal AccountPrincipal principal,
                                 @RequestBody UserOnboardingPayload.WorkPreferences body) {
        return editService.updateWork(accountId(principal), validate(body));
    }

    @PutMapping("links")
    public TalentDetailsDTO links(@AuthenticationPrincipal AccountPrincipal principal,
                                  @RequestBody UserOnboardingPayload.ProfileLinks body) {
        return editService.updateLinks(accountId(principal), validate(body));
    }

    @PutMapping("skills")
    public TalentDetailsDTO skills(@AuthenticationPrincipal AccountPrincipal principal,
                                   @RequestBody TalentProfileEdit.Skills body) {
        return editService.replaceSkills(accountId(principal), validate(body));
    }

    @PutMapping("languages")
    public TalentDetailsDTO languages(@AuthenticationPrincipal AccountPrincipal principal,
                                      @RequestBody TalentProfileEdit.Languages body) {
        return editService.replaceLanguages(accountId(principal), validate(body));
    }

    @PutMapping("experience")
    public TalentDetailsDTO experience(@AuthenticationPrincipal AccountPrincipal principal,
                                       @RequestBody TalentProfileEdit.Experience body) {
        return editService.replaceExperience(accountId(principal), validate(body));
    }

    @PutMapping("education")
    public TalentDetailsDTO education(@AuthenticationPrincipal AccountPrincipal principal,
                                      @RequestBody TalentProfileEdit.Education body) {
        return editService.replaceEducation(accountId(principal), validate(body));
    }

    @PutMapping("certifications")
    public TalentDetailsDTO certifications(@AuthenticationPrincipal AccountPrincipal principal,
                                           @RequestBody TalentProfileEdit.Certifications body) {
        return editService.replaceCertifications(accountId(principal), validate(body));
    }

    @PutMapping("courses")
    public TalentDetailsDTO courses(@AuthenticationPrincipal AccountPrincipal principal,
                                    @RequestBody TalentProfileEdit.Courses body) {
        return editService.replaceCourses(accountId(principal), validate(body));
    }

    @PutMapping("projects")
    public TalentDetailsDTO projects(@AuthenticationPrincipal AccountPrincipal principal,
                                     @RequestBody TalentProfileEdit.Projects body) {
        return editService.replaceProjects(accountId(principal), validate(body));
    }

    private static long accountId(AccountPrincipal principal) {
        if (principal == null) {
            throw new AuthenticationCredentialsNotFoundException("No session");
        }
        return principal.getAccountId();
    }

    private <T> T validate(T body) {
        Set<ConstraintViolation<T>> violations = validator.validate(body);

        if (!violations.isEmpty()) {
            throw new ApiPayloadValidationException("Validating the profile edit failed.", violations);
        }
        return body;
    }
}
