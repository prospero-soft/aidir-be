package ro.prospero.aidir.config;

import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import ro.prospero.aidir.interceptor.MetricInterceptor;

@Configuration
@AllArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    private MetricInterceptor metricInterceptor;
    private UploadsConfig uploadsConfig;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(metricInterceptor)
                .addPathPatterns("/api/**");
    }

    /**
     * Uploaded images are served straight off the uploads root, so what the database stores - a
     * root-relative path - is also what addresses the file from outside, with only the public base path
     * in front of it.
     *
     * <p>The location goes through {@code toUri()} rather than "file:" concatenation: the root is
     * already absolute and normalised, and on Windows the drive letter needs the URI form to resolve.
     * The interceptor above is on /api/** only, so nothing measures or intercepts these.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(uploadsConfig.getPublicBasePath() + "/**")
                .addResourceLocations(uploadsConfig.getRoot().toUri().toString());
    }
}
