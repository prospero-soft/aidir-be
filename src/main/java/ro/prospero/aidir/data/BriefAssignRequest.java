package ro.prospero.aidir.data;

import jakarta.validation.constraints.NotNull;

/** {@code editorId} is the admin account to write the brief. */
public record BriefAssignRequest(@NotNull Long editorId) {
}
