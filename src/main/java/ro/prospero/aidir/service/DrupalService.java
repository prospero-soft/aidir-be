package ro.prospero.aidir.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ro.prospero.aidir.config.DrupalConfig;
import ro.prospero.aidir.data.ToolDTO;
import ro.prospero.aidir.exception.DrupalException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class DrupalService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DrupalService.class);
    private final CloseableHttpClient httpClient;
    private final DrupalConfig drupalConfig;
    private final ObjectMapper objectMapper;

    public DrupalService(CloseableHttpClient httpClient, DrupalConfig drupalConfig) {
        this.httpClient = httpClient;
        this.drupalConfig = drupalConfig;
        this.objectMapper = new ObjectMapper();
    }

    public List<Map<String, Object>> getTools() {

        String url = drupalConfig.getUrl() + "/jsonapi/node/tool";
        HttpGet request = new HttpGet(url);
        request.addHeader("Accept", "application/vnd.api+json");
        request.addHeader("Authorization", basicAuthHeader());

        try {
            List<Map<String, Object>> tools = httpClient.execute(request, response -> {
                int status = response.getCode();
                String body = response.getEntity() != null
                        ? EntityUtils.toString(response.getEntity())
                        : "";

                if (status < 200 || status >= 300) {
                    throw new IOException("HTTP " + status + " - " + body);
                }

                return objectMapper.readValue(body, new TypeReference<List<Map<String, Object>>>() {
                });
            });

            return tools;
        } catch (IOException e) {
            LOGGER.error("Error when getting tool list from Drupal", e);
            throw new DrupalException();
        }
    }

    public Map<String, Object> createTool(ToolDTO tool)  {
        HttpPost request = new HttpPost(drupalConfig.getUrl() + "/jsonapi/node/tool");
        request.addHeader("Content-Type", "application/vnd.api+json");
        request.addHeader("Accept", "application/vnd.api+json");
        request.addHeader("Authorization", basicAuthHeader());

        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode data = root.putObject("data");
        data.put("type", "node--tool");

        ObjectNode attrs = data.putObject("attributes");
        // title is required
        attrs.put("title", tool.getName());
        attrs.put("field_name", tool.getName());
        attrs.put("field_category", tool.getCategory());
        attrs.put("field_id", tool.getId());
        attrs.put("field_long_description", tool.getLongDescription());
        attrs.put("field_short_description", tool.getShortDescription());
        if (tool.getTags() != null) {
            ArrayNode tagArray = attrs.putArray("field_hashtags");
            tool.getTags().forEach(tagArray::add);
        }

        //todo: pricing is a list(text) apparently, I'll have to figure out support for this in the UI/backend
        if (tool.getPricing() != null) {
            ArrayNode pricingArray = attrs.putArray("field_pricing");
            pricingArray.add(tool.getPricing());
        }

        // Link field
        if (tool.getUrl() != null) {
            ObjectNode urlNode = attrs.putObject("field_url");
            urlNode.put("uri", tool.getUrl());
//            if (tool.getUrlTitle() != null) {
//                urlNode.put("title", tool.getUrlTitle());
//            }
        }

        try {
            String jsonPayload = objectMapper.writeValueAsString(root);
            request.setEntity(new StringEntity(jsonPayload, StandardCharsets.UTF_8));

            return httpClient.execute(request, response -> {
                int status = response.getCode();
                String body = response.getEntity() != null
                        ? EntityUtils.toString(response.getEntity())
                        : "";

                if (status < 200 || status >= 300) {
                    throw new IOException("HTTP " + status + " - " + body);
                }

                return objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
            });

        } catch (IOException e) {
            LOGGER.error("Error when creating tool", e);
            throw new DrupalException();
        }
    }

    private String basicAuthHeader() {
        String creds = drupalConfig.getUser() + ":" + drupalConfig.getPassword();
        return "Basic " + Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8));
    }

}
