package ro.prospero.aidir.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.Data;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.stereotype.Service;
import ro.prospero.aidir.config.DirectusConfig;
import ro.prospero.aidir.data.ToolDTO;
import ro.prospero.aidir.event.LoyaltyEvent;
import ro.prospero.aidir.event.MetricEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
public class DirectusService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DirectusService.class);

    private final DirectusConfig directusConfig;
    private final CloseableHttpClient closeableHttpClient;
    private final ObjectMapper objectMapper;

    public DirectusService(DirectusConfig directusConfig, CloseableHttpClient closeableHttpClient) {
        this.directusConfig = directusConfig;
        this.closeableHttpClient = closeableHttpClient;
        this.objectMapper = Jackson2ObjectMapperBuilder.json()
                                                       .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                                                       .build();
    }

    public void uploadTool(ToolDTO tool) {
        String endpoint = directusConfig.getUrl() + "/items/tool";

        uploadItem(endpoint, tool);
    }

    public ToolDTO getTool(Long id) {
        String endpoint = directusConfig.getUrl() + "/items/tool/" + id;
        HttpGet getRequest = new HttpGet(endpoint);
        addHeaders(getRequest);

        try {
            return closeableHttpClient.execute(getRequest, response -> {
                String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                ToolResponseWrapper wrapper = objectMapper.readValue(body, ToolResponseWrapper.class);
                return wrapper.data;
            });
        } catch (IOException e) {
            LOGGER.error("Error when retrieving from: {}", endpoint, e);
            throw new RuntimeException(e);
        }
    }

    @Data
    private static class ToolResponseWrapper {
        ToolDTO data;
    }

    public List<ToolDTO> getAllTools() {
        String endpoint = directusConfig.getUrl() + "/items/tool";
        HttpGet getRequest = new HttpGet(endpoint);
        addHeaders(getRequest);

        try {
            return closeableHttpClient.execute(getRequest, response -> {
                String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

                ToolListResponseWrapper wrapper = objectMapper.readValue(body, ToolListResponseWrapper.class);
                return wrapper.data;
            });
        } catch (IOException e) {
            LOGGER.error("Error when retrieving from: {}", endpoint, e);
            throw new RuntimeException(e);
        }
    }

    @Data
    private static class ToolListResponseWrapper {
        List<ToolDTO> data;
    }

    private void uploadItem(String endpoint, Object payload) {
        try {
            HttpPost post = new HttpPost(endpoint);

            // Auth + headers
            addHeaders(post);

            // Serialize DTO to JSON
            String jsonBody = objectMapper.writeValueAsString(payload);
            post.setEntity(new StringEntity(jsonBody, StandardCharsets.UTF_8));

            LOGGER.info("Posting to: {}", endpoint);
            LOGGER.info("Body: {}", jsonBody);
            closeableHttpClient.execute(post, basicResponseHandler(jsonBody));

        } catch (IOException e) {
            LOGGER.error("Error when posting to {} :", endpoint, e);
            throw new RuntimeException(e);
        }
    }

    public void uploadMetricEvent(MetricEvent event) {
        String endpoint = directusConfig.getUrl() + "/items/feature_metrics";

        uploadItem(endpoint, event);
    }

    public void uploadLoyaltyEvent(LoyaltyEvent event) {
        String endpoint = directusConfig.getUrl() + "/items/loyalty_events";

        uploadItem(endpoint, event);
    }

    private void createCollection(String collectionName,
                                  Map<String, Object> collectionDefinition,
                                  List<Map<String, Object>> fieldDefinitions) { // as per directus docs: https://directus.io/docs/api/collections#create-a-collection + https://directus.io/docs/api/fields#create-field-in-collection
        String collectionsEndpoint = directusConfig.getUrl() + "/collections";

        try {
            HttpPost createCollectionRequest = new HttpPost(collectionsEndpoint);

            // Auth + headers
            addHeaders(createCollectionRequest);

            String createCollectionRequestBody = objectMapper.writeValueAsString(collectionDefinition);
            createCollectionRequest.setEntity(new StringEntity(createCollectionRequestBody, StandardCharsets.UTF_8));
            LOGGER.info("Posting to {}", collectionsEndpoint);

            closeableHttpClient.execute(createCollectionRequest, basicResponseHandler(createCollectionRequestBody));

            String fieldEndpoint = directusConfig.getUrl() + "/fields/" + collectionName;

            for (Map<String, Object> fieldDefinition : fieldDefinitions) {
                HttpPost createFieldRequest = new HttpPost(fieldEndpoint);

                addHeaders(createFieldRequest);
                String createFieldRequestBody = objectMapper.writeValueAsString(fieldDefinition);
                createFieldRequest.setEntity(new StringEntity(createFieldRequestBody, StandardCharsets.UTF_8));
                LOGGER.info("Posting to {}", fieldDefinition);

                closeableHttpClient.execute(createFieldRequest, basicResponseHandler(createFieldRequestBody));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private static HttpClientResponseHandler<String> basicResponseHandler(String createCollectionRequestBody) {
        return response -> {
            int statusCode = response.getCode();
            if (statusCode != HttpStatus.SC_OK && statusCode != HttpStatus.SC_CREATED) {
                LOGGER.error("Error when calling directus API: request body={}, response={}", createCollectionRequestBody, response);
                throw new RuntimeException("Failed Directus call. HTTP " + statusCode);
            }

            return "Ok";
        };
    }

    private void addHeaders(BasicHttpRequest request) {
        request.addHeader("Authorization", "Bearer " + directusConfig.getApiToken());
        request.addHeader("Content-Type", "application/json");
        request.addHeader("Accept", "application/json");
    }

    public void createMetricsCollection() {
        String metricsCollectionName = "feature_metrics";
        Map<String, Object> metricsCollectionDefinition = Map.of(
                "collection", metricsCollectionName,
                "schema", Map.of()
        );

        List<Map<String, Object>> metricsFieldsDefinitions = List.of(
                Map.of(
                        "field", "featureName",
                        "type", "string",
                        "schema", Map.of(
                                "is_nullable", false
                        ),
                        "meta", Map.of(
                                "interface", "input",
                                "required", true,
                                "width", "full",
                                "readonly", true
                        )
                ),
                Map.of(
                        "field", "endpoint",
                        "type", "string",
                        "schema", Map.of(
                                "is_nullable", false
                        ),
                        "meta", Map.of(
                                "interface", "input",
                                "required", true,
                                "width", "full",
                                "readonly", true
                        )
                ),
                Map.of(
                        "field", "timestamp",
                        "type", "dateTime",
                        "schema", Map.of(
                                "is_nullable", false
                        ),
                        "meta", Map.of(
                                "interface", "datetime",
                                "required", true,
                                "width", "half",
                                "readonly", true
                        )
                )
        );

        createCollection(metricsCollectionName, metricsCollectionDefinition, metricsFieldsDefinitions);
    }

    public void createLoyaltyCollection() {
        String collectionName = "loyalty_events";
        Map<String, Object> collectionDefinition = Map.of(
                "collection", collectionName,
                "schema", Map.of()
        );

        List<Map<String, Object>> fieldDefinitions = List.of(
                Map.of(
                        "field", "userId",
                        "type", "bigInteger",
                        "schema", Map.of(
                                "is_nullable", false
                        ),
                        "meta", Map.of(
                                "interface", "input",
                                "required", true,
                                "width", "half",
                                "readonly", true
                        )
                ),
                Map.of(
                        "field", "amount",
                        "type", "bigInteger",
                        "schema", Map.of(
                                "is_nullable", false
                        ),
                        "meta", Map.of(
                                "interface", "input",
                                "required", true,
                                "width", "half",
                                "readonly", true
                        )
                ),
                Map.of(
                        "field", "action",
                        "type", "string",
                        "schema", Map.of(
                                "max_length", 255,
                                "is_nullable", false
                        ),
                        "meta", Map.of(
                                "interface", "input",
                                "required", true,
                                "width", "full"
                        )
                ),
                Map.of(
                        "field", "timestamp",
                        "type", "dateTime",
                        "schema", Map.of(
                                "is_nullable", false
                        ),
                        "meta", Map.of(
                                "interface", "datetime",
                                "required", true,
                                "width", "half",
                                "readonly", true
                        )
                )
        );

        createCollection(collectionName, collectionDefinition, fieldDefinitions);
    }


}
