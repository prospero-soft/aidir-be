package ro.prospero.aidir.event;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import static ro.prospero.aidir.config.AsyncConfig.METRICS_EXECUTOR;

@Component
public class MetricEventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetricEventListener.class);

    @Async(METRICS_EXECUTOR)
    @EventListener
    public void onMetricEvent(MetricEvent event) {
        LOGGER.info("Feature used: feature={}, endpoint={}, timestamp={}",
                    event.featureName(), event.endpoint(), event.timestamp());
    }

}
