package ro.prospero.aidir.service;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ro.prospero.aidir.config.StrapiConfig;
import ro.prospero.aidir.data.ToolDTO;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
@AllArgsConstructor
public class StrapiService {
    private CloseableHttpClient httpClient;
    private StrapiConfig strapiConfig;
    private static final Logger LOGGER = LoggerFactory.getLogger(StrapiService.class);


    public void uploadTool(ToolDTO tool) {
        LOGGER.info("Strapi url: {}", strapiConfig.getUrl());
        HttpPost post = new HttpPost(strapiConfig.getUrl() + "/api/tools");
        post.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + strapiConfig.getApiToken());
        post.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");

        String body = buildRequestBody(tool);
        LOGGER.info("Strapi request body: ");
        LOGGER.info("{}",body);
        post.setEntity(new StringEntity(body, StandardCharsets.UTF_8));

        try {
            httpClient.execute(post, response -> {
                int status = response.getCode();
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

                if (status >= 200 && status < 300) {
                    return responseBody;
                }
                throw new IllegalStateException(
                        "Strapi returned status " + status + " with body: " + responseBody);
            });


        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String buildRequestBody(ToolDTO tool) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();

            ObjectNode root = objectMapper.createObjectNode();
            ObjectNode data = root.putObject("data");

            if (tool.id() != null) {
                data.put("tool_id", tool.id());
            }
            data.put("name", tool.name());
            data.put("url", tool.url());

            // pricing is JSON in Strapi, supplied as a String in the DTO
            if (tool.pricing() != null) {
                data.put("pricing", tool.pricing());
            }

            data.put("shortDescription", tool.shortDescription());
            data.put("longDescription", tool.longDescription());
            // The Strapi model has one category; the DTO carries every one the tool was listed under.
            if (tool.categories() != null && !tool.categories().isEmpty()) {
                data.put("category", tool.categories().getFirst());
            }

            // tags is JSON in Strapi, List<String> in the DTO
            if (tool.tags() != null) {
                data.set("tags", objectMapper.valueToTree(tool.tags()));
            }

            data.put("approved", tool.approvedAt() != null);
            if (tool.submittedAt() != null) {
                data.put("submittedAt", tool.submittedAt().toString());
            }

            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            LOGGER.error("Error building Strapi request body", e);
            throw new RuntimeException(e);
        }
    }
}
