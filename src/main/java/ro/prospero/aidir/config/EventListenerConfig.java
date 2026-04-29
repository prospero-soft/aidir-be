package ro.prospero.aidir.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ro.prospero.aidir.event.LoyaltyEventListener;
import ro.prospero.aidir.event.MetricEventListener;

@Configuration
public class EventListenerConfig {

    @Bean
    public MetricEventListener metricEventListener () {
        return new MetricEventListener();
    }

    @Bean
    public LoyaltyEventListener loyaltyEventListener() {
        return new LoyaltyEventListener();
    }
}
