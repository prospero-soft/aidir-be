package ro.prospero.aidir.event;

import lombok.AllArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
@AllArgsConstructor
public class MetricEventPublisher {

    public ApplicationEventPublisher applicationEventPublisher;

    public void publish(String featureName, String endpoint) {
        MetricEvent event = new MetricEvent(featureName, endpoint, OffsetDateTime.now());
        applicationEventPublisher.publishEvent(event);
    }


}
