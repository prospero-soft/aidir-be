package ro.prospero.aidir.data;

/**
 * What the selected locations are taken to mean.
 *
 * <p>{@code talent_profile} holds one location per profile, so "current" is the only thing a location
 * actually is. The other two widen the match using {@code work_relocation}, which is why this is a scope on
 * the location filter rather than a filter of its own.
 */
public enum TalentLocationScope {
    /** Only profiles based in one of the selected locations. */
    CURRENT,
    /** Based there, or willing to move. */
    CURRENT_OR_RELOCATE,
    /** Willing to move, wherever they are now - the selected locations stop narrowing anything. */
    RELOCATE_ONLY
}
