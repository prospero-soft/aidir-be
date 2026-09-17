package ro.prospero.aidir.data;

import java.util.List;

/**
 * One public talent profile: the directory row plus the history hanging off it.
 *
 * <p>The directory row is nested rather than restated - the profile page shows the same name, title,
 * location and skills the card does, and one shape for both is what keeps them from drifting.
 *
 * <p>The history records mirror the ones in {@link UserOnboardingPayload}, so what the wizard writes is
 * what the profile reads back. They stay separate types because these carry the row ids.
 */
public record TalentDetailsDTO(TalentCardDTO card,
                               Links links,
                               List<Language> languages,
                               List<Experience> experience,
                               List<Education> education,
                               List<Certification> certifications,
                               List<Course> courses,
                               List<Project> projects,
                               List<TalentCardDTO> similar) {

    public record Links(String linkedin, String github, String portfolio, String other) {
    }

    /** The card carries language names only; the profile page shows the level beside each one. */
    public record Language(String language, String proficiency) {
    }

    public record Experience(Long id,
                             String company,
                             String position,
                             String location,
                             String workplace,
                             String period,
                             String description) {
    }

    public record Education(Long id,
                            String institution,
                            String degree,
                            String field,
                            String graduationYear,
                            String location) {
    }

    public record Certification(Long id, String name, String organization, String year) {
    }

    public record Course(Long id, String name, String provider, String focus, String completionYear) {
    }

    public record Project(Long id, String title, String category, String year, String description) {
    }
}
