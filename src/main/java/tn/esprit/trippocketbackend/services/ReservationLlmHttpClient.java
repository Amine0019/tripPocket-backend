package tn.esprit.trippocketbackend.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible chat-completion client (Groq by default) for the Reservation AI assistant.
 * Configuration (all optional, set in application-local.properties):
 * reservation.ai.api-key, reservation.ai.base-url, reservation.ai.model, reservation.ai.timeout-ms.
 * The API key is never logged.
 */
@Slf4j
@Component
public class ReservationLlmHttpClient implements ReservationLlmClient {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public ReservationLlmHttpClient(ObjectMapper objectMapper,
                                    @Value("${reservation.ai.api-key:}") String apiKey,
                                    @Value("${reservation.ai.base-url:https://api.groq.com/openai/v1}") String baseUrl,
                                    @Value("${reservation.ai.model:openai/gpt-oss-20b}") String model,
                                    @Value("${reservation.ai.timeout-ms:20000}") int timeoutMs) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        this.model = model;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.min(timeoutMs, 10_000));
        factory.setReadTimeout(timeoutMs);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isBlank() && !baseUrl.isBlank();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt, int maxTokens) {
        if (!isConfigured()) {
            throw new LlmException("AI is not configured (reservation.ai.api-key is empty)");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)));
        body.put("temperature", 0.3);
        body.put("max_completion_tokens", maxTokens);
        body.put("stream", false);
        if (model != null && model.contains("gpt-oss")) {
            body.put("reasoning_effort", "low");
        }

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + "/chat/completions", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            String text = objectMapper.readTree(response.getBody())
                    .path("choices").path(0).path("message").path("content").asString("").trim();
            if (text.isEmpty()) {
                throw new LlmException("The AI returned an empty answer");
            }
            return text;
        } catch (RestClientResponseException e) {
            String details = e.getResponseBodyAsString();
            log.warn("Reservation AI call refused: HTTP {} {}", e.getStatusCode().value(),
                    details.length() > 200 ? details.substring(0, 200) : details);
            throw new LlmException("The AI service answered HTTP " + e.getStatusCode().value());
        } catch (RestClientException e) {
            log.warn("Reservation AI service unreachable: {}", e.getMessage());
            throw new LlmException("The AI service cannot be reached");
        } catch (JacksonException e) {
            throw new LlmException("Unreadable AI response");
        }
    }
}
