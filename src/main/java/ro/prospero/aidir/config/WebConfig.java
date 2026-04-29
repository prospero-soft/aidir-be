package ro.prospero.aidir.config;

import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import ro.prospero.aidir.interceptor.MetricInterceptor;

@Configuration
@AllArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    private MetricInterceptor metricInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(metricInterceptor)
                .addPathPatterns("/api/**");
    }
}
