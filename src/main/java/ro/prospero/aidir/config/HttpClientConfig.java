package ro.prospero.aidir.config;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HttpClientConfig {

    @Bean
    public CloseableHttpClient httpClient() {
        return HttpClients.custom().build();
    }

    @Value("${drupal.url}")
    private String drupalUrl;
    @Value("${drupal.user}")
    private String drupalUser;
    @Value("${drupal.password}")
    private String drupalPassword;

    @Value("${strapi.api.url}")
    private String strapiUrl;

    @Value("${strapi.api.token}")
    private String strapiApiToken;

    @Value("${directus.api.url}")
    private String directusUrl;

    @Value("${directus.api.token}")
    private String directusApiToken;

    @Bean
    public DrupalConfig drupalConfig() {
        return new DrupalConfig(drupalUrl, drupalUser, drupalPassword);
    }

    @Bean
    public StrapiConfig strapiConfig() {
        return new StrapiConfig(strapiUrl, strapiApiToken);
    }

    @Bean
    public DirectusConfig directusConfig() {
        return  new DirectusConfig(directusUrl, directusApiToken);
    }
}
