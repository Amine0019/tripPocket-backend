package tn.esprit.trippocketbackend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatbotUserConfigTest {

    @Test
    @DisplayName("isConfigured - should return false when apiKey is null or blank")
    void isConfigured_FalseWhenNullOrBlank() {
        ChatbotUserConfig config = new ChatbotUserConfig();

        config.setApiKey(null);
        assertFalse(config.isConfigured());

        config.setApiKey("");
        assertFalse(config.isConfigured());

        config.setApiKey("    ");
        assertFalse(config.isConfigured());
    }

    @Test
    @DisplayName("isConfigured - should return true when apiKey is present")
    void isConfigured_TrueWhenPresent() {
        ChatbotUserConfig config = new ChatbotUserConfig();
        config.setApiKey("test-api-key");
        config.setModel("gemini-3.8-flash");
        config.setBaseUrl("https://generativelanguage.googleapis.com/v1beta/models");
        config.setTimeoutMs(15000);

        assertTrue(config.isConfigured());
        assertEquals("test-api-key", config.getApiKey());
        assertEquals("gemini-3.8-flash", config.getModel());
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models", config.getBaseUrl());
        assertEquals(15000, config.getTimeoutMs());
    }
}
