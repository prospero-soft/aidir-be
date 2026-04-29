package ro.prospero.aidir.event;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.time.OffsetDateTime;

@Service
@AllArgsConstructor
public class LoyaltyEventPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoyaltyEventPublisher.class);
    public ApplicationEventPublisher applicationEventPublisher;

    public void publish(Long userId, BigInteger amount, String action) {
        LoyaltyEvent event = new LoyaltyEvent(userId, amount, action, OffsetDateTime.now());
        applicationEventPublisher.publishEvent(event);
    }

}
