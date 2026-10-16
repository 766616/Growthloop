package com.growthloop.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growthloop.exception.ApiException;
import com.growthloop.util.M;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/** HTTP client for the Python FastAPI AI service. Failures never crash the app. */
@Service
public class AiClient {
    private static final Logger log = LoggerFactory.getLogger(AiClient.class);
    public static final String UNAVAILABLE =
            "The AI service is temporarily unavailable. Your last saved analysis is still shown - try again in a minute.";

    private final RestClient client;
    private final ObjectMapper mapper;

    public AiClient(@Value("${app.ai.base-url}") String baseUrl,
                    @Value("${app.ai.api-key:}") String apiKey,
                    @Value("${app.ai.timeout-ms:15000}") int timeoutMs,
                    ObjectMapper mapper) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Math.min(timeoutMs, 5000));
        f.setReadTimeout(timeoutMs);
        RestClient.Builder b = RestClient.builder().baseUrl(baseUrl).requestFactory(f);
        if (apiKey != null && !apiKey.isBlank()) b.defaultHeader("X-AI-KEY", apiKey);
        this.client = b.build();
        this.mapper = mapper;
    }

    public JsonNode post(String path, Object body) {
        try {
            String raw = client.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().body(String.class);
            return mapper.readTree(raw);
        } catch (HttpClientErrorException e) {
            // 4xx from the AI service = our input was not analysable (e.g. no career goal)
            String detail = extractDetail(e.getResponseBodyAsString());
            log.warn("AI service rejected request {}: {}", path, detail);
            throw ApiException.badRequest(detail);
        } catch (RestClientException e) {
            log.error("AI service call {} failed: {}", path, e.getMessage());
            throw ApiException.unavailable(UNAVAILABLE);
        } catch (Exception e) {
            log.error("AI service response for {} unreadable", path, e);
            throw ApiException.unavailable(UNAVAILABLE);
        }
    }

    public Map<String, Object> health() {
        try {
            String raw = client.get().uri("/health").retrieve().body(String.class);
            return M.of("available", true, "details", mapper.readValue(raw, Object.class));
        } catch (Exception e) {
            return M.of("available", false, "message", UNAVAILABLE);
        }
    }

    private String extractDetail(String body) {
        try {
            JsonNode n = mapper.readTree(body);
            if (n.has("errors")) return n.path("detail").asText("Invalid input") + ": " + n.get("errors").toString();
            return n.path("detail").asText("The AI service could not analyse this profile");
        } catch (Exception ex) {
            return "The AI service could not analyse this profile";
        }
    }
}
