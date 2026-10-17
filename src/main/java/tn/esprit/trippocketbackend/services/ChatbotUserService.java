package tn.esprit.trippocketbackend.services;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.trippocketbackend.config.ChatbotUserConfig;
import tn.esprit.trippocketbackend.dto.ChatbotUserMessageDto;
import tn.esprit.trippocketbackend.dto.ChatbotUserRequest;
import tn.esprit.trippocketbackend.dto.ChatbotUserResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatbotUserService {

    private static final int MAX_HISTORY_MESSAGES = 10;
    private static final String SYSTEM_INSTRUCTION = """
            You are TripPocket AI, the intelligent, friendly, and helpful travel assistant for the TripPocket travel application.
            IMPORTANT: Always respond in clear, natural English, regardless of the language used by the user.
            Your mission:
            - Answer questions in natural language, offering expert travel tips, destination recommendations, and cultural insights.
            - Help travelers organize their ideas, pack efficiently, plan daily itineraries, and manage their trip budget.
            - Explain how to use TripPocket features: exploring trips, reserving activities, tracking expenses by category, and viewing traveler profiles.
            - Understand previous messages in the conversation to maintain a coherent and engaging dialogue.
            - Keep a warm, polite, encouraging, and professional tone.
            - Use clean formatting (bullet points, bold highlights) when presenting lists or recommendations.
            - If asked about personal data or real-time external bookings that you don't possess, answer honestly and courteously without inventing details.
            - You are an informational assistant and do not perform direct database modifications.
            """;

    private final ChatbotUserConfig chatbotUserConfig;
    private final ObjectMapper objectMapper;

    private RestTemplate restTemplate;

    private synchronized RestTemplate getRestTemplate() {
        if (restTemplate == null) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(chatbotUserConfig.getTimeoutMs());
            factory.setReadTimeout(chatbotUserConfig.getTimeoutMs());
            restTemplate = new RestTemplate(factory);
        }
        return restTemplate;
    }

    private volatile String cachedWorkingModel = null;

    private String getWorkingModel() {
        if (cachedWorkingModel != null) {
            return cachedWorkingModel;
        }
        return chatbotUserConfig.getModel();
    }

    public ChatbotUserResponse chat(ChatbotUserRequest request) {
        if (!chatbotUserConfig.isConfigured()) {
            log.warn("Gemini API key is not configured. Request cannot be processed.");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The TripPocket AI assistant is not configured. Please set the GEMINI_API_KEY environment variable.");
        }

        String apiKey = chatbotUserConfig.getApiKey().trim();

        // Validate key is not a placeholder
        if (apiKey.startsWith("REPLACE_") || apiKey.equalsIgnoreCase("YOUR_KEY_HERE")) {
            log.error("Gemini API key is still set to a placeholder value.");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The Gemini API key has not been configured. Get a key at https://aistudio.google.com/apikey and set GEMINI_API_KEY.");
        }

        // Safe diagnostic: log key prefix (first 3 chars) + key length, never the full key
        String keyPrefix = apiKey.substring(0, Math.min(3, apiKey.length()));
        log.debug("Gemini API call — key prefix='{}...', key length={}, model='{}', baseUrl='{}'",
                keyPrefix, apiKey.length(), getWorkingModel(), chatbotUserConfig.getBaseUrl());

        String userMessage = request.getMessage() != null ? request.getMessage().trim() : "";
        if (userMessage.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The message cannot be empty.");
        }

        String modelToUse = getWorkingModel();
        Map<String, Object> requestBody = buildGeminiPayload(userMessage, request.getHistory());

        // Build authenticated headers
        // x-goog-api-key is required for AQ. authorization keys and also works with legacy AIzaSy keys
        HttpHeaders headers = buildAuthHeaders(apiKey);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        // Build URL with ?key= query parameter (belt-and-suspenders: header + query param)
        String url = String.format("%s/%s:generateContent?key=%s",
                chatbotUserConfig.getBaseUrl(), modelToUse, apiKey);

        log.debug("Gemini request — model='{}', endpoint='{}/{}:generateContent'",
                modelToUse, chatbotUserConfig.getBaseUrl(), modelToUse);

        try {
            ResponseEntity<String> response = getRestTemplate().exchange(
                    url, HttpMethod.POST, entity, String.class);

            log.debug("Gemini API responded — HTTP {}", response.getStatusCode().value());

            String replyText = parseGeminiResponse(response.getBody());
            return ChatbotUserResponse.builder()
                    .reply(replyText)
                    .timestamp(Instant.now().toString())
                    .build();

        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String responseBody = e.getResponseBodyAsString();
            log.error("Gemini API error — HTTP {}, model='{}', key prefix='{}...', response: {}",
                    status, modelToUse, keyPrefix, responseBody);

            // If 404 (model not found), try auto-discovery from ListModels
            if (status == 404 && cachedWorkingModel == null) {
                log.warn("Model '{}' returned 404. Attempting automatic model discovery via ListModels...", modelToUse);
                String discovered = discoverSupportedModel(apiKey);
                if (discovered != null) {
                    log.info("Discovered working model '{}'. Retrying request...", discovered);
                    try {
                        String retryUrl = String.format("%s/%s:generateContent?key=%s",
                                chatbotUserConfig.getBaseUrl(), discovered, apiKey);

                        ResponseEntity<String> retryResponse = getRestTemplate().exchange(
                                retryUrl, HttpMethod.POST, entity, String.class);

                        String replyText = parseGeminiResponse(retryResponse.getBody());
                        return ChatbotUserResponse.builder()
                                .reply(replyText)
                                .timestamp(Instant.now().toString())
                                .build();
                    } catch (Exception retryEx) {
                        log.error("Retry with discovered model '{}' also failed: {}", discovered, retryEx.getMessage());
                    }
                }
            }

            if (status == 400) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Request format not supported by Gemini. Please try again with a different message.");
            } else if (status == 401 || status == 403) {
                log.error("Authentication failure (HTTP {}). Key prefix='{}...', key length={}. " +
                        "Verify the key was generated at https://aistudio.google.com/apikey " +
                        "and that the Generative Language API is enabled.",
                        status, keyPrefix, apiKey.length());
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Gemini API key invalid or rejected (HTTP " + status + "). " +
                        "Verify that your key was generated from Google AI Studio and that the Generative Language API is enabled.");
            } else if (status == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Gemini model '" + modelToUse + "' not found. " +
                        "Check the model name in application.properties (gemini.api.model).");
            } else if (status == 429) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Gemini rate limit reached. Please wait a few moments before trying again.");
            } else if (status >= 500) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "The Gemini service is temporarily unavailable (HTTP " + status + "). Please try again later.");
            }

            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Error communicating with the AI assistant (HTTP " + status + ").");

        } catch (RestClientException e) {
            log.error("Network error reaching Gemini API: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                    "Unable to reach the Gemini service (timeout or network error).");
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception e) {
            log.error("Unexpected error in ChatbotUserService", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "An unexpected error occurred while processing your message.");
        }
    }

    /**
     * Builds HTTP headers with authentication for the Gemini API.
     * Uses x-goog-api-key header (required for AQ. authorization keys, also works with AIzaSy keys).
     */
    private HttpHeaders buildAuthHeaders(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey);
        return headers;
    }

    /**
     * Discovers a supported model by calling the ListModels endpoint.
     * Prefers models with "flash" in the name that support generateContent.
     * Does NOT hardcode any model name — uses whatever the API returns.
     */
    private String discoverSupportedModel(String apiKey) {
        String[] candidateBases = new String[] {
                "https://generativelanguage.googleapis.com/v1beta/models",
                "https://generativelanguage.googleapis.com/v1/models"
        };

        HttpHeaders listHeaders = buildAuthHeaders(apiKey);
        HttpEntity<?> listEntity = new HttpEntity<>(listHeaders);

        for (String base : candidateBases) {
            try {
                String listUrl = String.format("%s?key=%s", base, apiKey);
                log.debug("ListModels probe on '{}'", base);
                ResponseEntity<String> resp = getRestTemplate().exchange(
                        listUrl, HttpMethod.GET, listEntity, String.class);
                JsonNode root = objectMapper.readTree(resp.getBody());
                JsonNode models = root.path("models");

                if (models.isArray() && !models.isEmpty()) {
                    String bestCandidate = null;
                    for (JsonNode m : models) {
                        String name = m.path("name").asText("");
                        JsonNode methods = m.path("supportedGenerationMethods");
                        boolean supportsGen = false;
                        if (methods.isArray()) {
                            for (JsonNode method : methods) {
                                if ("generateContent".equalsIgnoreCase(method.asText())) {
                                    supportsGen = true;
                                    break;
                                }
                            }
                        }

                        if (supportsGen) {
                            String clean = name.startsWith("models/") ? name.substring("models/".length()) : name;
                            log.debug("ListModels found generateContent-capable model: '{}'", clean);
                            if (clean.contains("flash")) {
                                bestCandidate = clean;
                                break; // Prefer first flash model found
                            } else if (bestCandidate == null) {
                                bestCandidate = clean;
                            }
                        }
                    }

                    if (bestCandidate != null) {
                        log.info("Auto-discovered model '{}' from endpoint '{}'", bestCandidate, base);
                        chatbotUserConfig.setBaseUrl(base);
                        this.cachedWorkingModel = bestCandidate;
                        return bestCandidate;
                    }
                }
            } catch (Exception ex) {
                log.warn("ListModels probe on '{}' failed: {}", base, ex.getMessage());
            }
        }
        log.error("Model auto-discovery failed — no generateContent-capable model found on any endpoint.");
        return null;
    }

    private Map<String, Object> buildGeminiPayload(String currentMessage, List<ChatbotUserMessageDto> history) {
        Map<String, Object> payload = new HashMap<>();

        // 1. System instruction
        Map<String, Object> systemPart = Map.of("text", SYSTEM_INSTRUCTION);
        payload.put("systemInstruction", Map.of("parts", List.of(systemPart)));

        // 2. Contents with bounded history
        List<Map<String, Object>> contents = new ArrayList<>();

        if (history != null && !history.isEmpty()) {
            int startIdx = Math.max(0, history.size() - MAX_HISTORY_MESSAGES);
            for (int i = startIdx; i < history.size(); i++) {
                ChatbotUserMessageDto msg = history.get(i);
                if (msg == null || msg.getContent() == null || msg.getContent().trim().isEmpty()) {
                    continue;
                }
                String role = "model".equalsIgnoreCase(msg.getRole()) || "assistant".equalsIgnoreCase(msg.getRole())
                        ? "model"
                        : "user";
                contents.add(Map.of(
                        "role", role,
                        "parts", List.of(Map.of("text", msg.getContent().trim()))
                ));
            }
        }

        // Add current user message
        contents.add(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", currentMessage))
        ));

        payload.put("contents", contents);

        // 3. Generation config
        payload.put("generationConfig", Map.of(
                "temperature", 0.7,
                "maxOutputTokens", 1200
        ));

        return payload;
    }

    private String parseGeminiResponse(String responseJson) {
        if (responseJson == null || responseJson.isBlank()) {
            return "I couldn't get a response. Please try rephrasing your question.";
        }

        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode firstCandidate = candidates.get(0);
                JsonNode parts = firstCandidate.path("content").path("parts");
                if (parts.isArray() && !parts.isEmpty()) {
                    StringBuilder replyBuilder = new StringBuilder();
                    for (JsonNode part : parts) {
                        String text = part.path("text").asText("");
                        replyBuilder.append(text);
                    }
                    String finalReply = replyBuilder.toString().trim();
                    if (!finalReply.isEmpty()) {
                        return finalReply;
                    }
                }
            }

            JsonNode promptFeedback = root.path("promptFeedback");
            if (promptFeedback.has("blockReason")) {
                return "Your message was filtered for safety or moderation reasons. Please try rephrasing your question.";
            }

        } catch (Exception e) {
            log.error("Error parsing Gemini response JSON", e);
        }

        return "I'm sorry, I couldn't formulate a response. Feel free to ask another question!";
    }
}
