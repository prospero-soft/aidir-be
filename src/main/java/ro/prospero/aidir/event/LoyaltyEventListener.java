package ro.prospero.aidir.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import static ro.prospero.aidir.config.AsyncConfig.LOYALTY_EXECUTOR;

@Component
public class LoyaltyEventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoyaltyEventListener.class);

    @Async(LOYALTY_EXECUTOR)
    @EventListener
    public void onLoyaltyEvent(LoyaltyEvent event) {
        LOGGER.info("Loyalty event: userId={}, amount={}, action={}, timestamp={}",
                    event.userId(), event.amount(), event.action(), event.timestamp());
    }
}
