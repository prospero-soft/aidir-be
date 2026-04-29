package ro.prospero.aidir.event;

import java.math.BigInteger;
import java.time.OffsetDateTime;

public record LoyaltyEvent(
        Long userId,
        BigInteger amount,
        String action,
        OffsetDateTime timestamp
) {
}
