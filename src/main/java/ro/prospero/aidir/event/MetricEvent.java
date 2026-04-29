package ro.prospero.aidir.event;

import java.time.OffsetDateTime;

public record MetricEvent(
        String featureName,
        String endpoint,
        OffsetDateTime timestamp
) {
}
