package ro.prospero.aidir.data;

/**
 * Mirrors Principal in aidir-fe (src/types/principal.ts).
 *
 * <p>{@code planKey} is null for an admin, which has no subscription. {@code avatar} is always null:
 * nothing stores one yet, and the front end falls back to its placeholder.
 */
public record PrincipalDTO(long accountId,
                           String email,
                           String name,
                           String accountType,
                           String planKey,
                           String avatar) {
}
