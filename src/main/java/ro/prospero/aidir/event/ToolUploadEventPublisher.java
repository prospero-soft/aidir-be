package ro.prospero.aidir.event;

import lombok.AllArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import ro.prospero.aidir.data.ToolDTO;

@Service
@AllArgsConstructor
public class ToolUploadEventPublisher {

    private ApplicationEventPublisher applicationEventPublisher;

    public void publish(ToolDTO tool) {
        applicationEventPublisher.publishEvent(new ToolUploadEvent(tool));
    }
}
