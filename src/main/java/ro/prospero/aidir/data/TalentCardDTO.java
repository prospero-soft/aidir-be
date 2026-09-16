package ro.prospero.aidir.data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * One row of the talent directory. Every field is a column: the card on screen also shows a verified badge,
 * a featured badge, an open-to-work dot, years of experience, a seniority label and a match score, none of
 * which exist in the schema, so the front end stands those in itself rather than having them invented here
 * and travel over the wire looking authoritative.
 *
 * <p>{@code id} is {@code talent_profile.account_id}, which puts an account id in profile URLs. A dedicated
 * public profile id is the eventual fix; it is a numeric id either way, so nothing downstream changes when
 * that lands.
 *
 * <p>{@code workEmploymentType} is singular because the column is. Onboarding collects one preference per
 * profile, not a set.
 */
public record TalentCardDTO(Long id,
                            String fullName,
                            String title,
                            String shortDescription,
                            String summary,
                            String country,
                            String workLocation,
                            String workRelocation,
                            String workWorkplace,
                            String workEmploymentType,
                            List<String> skills,
                            List<String> languages,
                            OffsetDateTime createdAt) {
}
