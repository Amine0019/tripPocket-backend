package tn.esprit.trippocketbackend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
@Setter
public class ChatbotUserConfig {

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.api.model:gemini-3.8-flash}")
    private String model;

    @Value("${gemini.api.base-url:https://generativelanguage.googleapis.com/v1beta/models}")
    private String baseUrl;

    @Value("${gemini.api.timeout-ms:30000}")
    private int timeoutMs;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }
}
