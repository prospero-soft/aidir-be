package ro.prospero.aidir.data;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record FreeUserOnboardingPayload(@Valid @NotNull UserBasic basic) {
}
