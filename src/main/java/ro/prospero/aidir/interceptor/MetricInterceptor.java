package ro.prospero.aidir.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import ro.prospero.aidir.event.MetricEventPublisher;

@Component
@AllArgsConstructor
public class MetricInterceptor implements HandlerInterceptor {
    private MetricEventPublisher metricEventPublisher;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        String requestUri = request.getRequestURI();
        String featureName = "TODO";

        metricEventPublisher.publish(featureName, requestUri);
    }
}
