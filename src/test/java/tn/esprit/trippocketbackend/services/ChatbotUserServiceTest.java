package tn.esprit.trippocketbackend.services;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.trippocketbackend.config.ChatbotUserConfig;
import tn.esprit.trippocketbackend.dto.ChatbotUserMessageDto;
import tn.esprit.trippocketbackend.dto.ChatbotUserRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatbotUserServiceTest {

    @Mock
    private ChatbotUserConfig chatbotUserConfig;

    private ObjectMapper objectMapper;
    private ChatbotUserService chatbotUserService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        chatbotUserService = new ChatbotUserService(chatbotUserConfig, objectMapper);
    }

    @Test
    @DisplayName("Should throw SERVICE_UNAVAILABLE when Gemini API key is missing")
    void shouldThrowWhenApiKeyNotConfigured() {
        when(chatbotUserConfig.isConfigured()).thenReturn(false);

        ChatbotUserRequest request = ChatbotUserRequest.builder()
                .message("Hello")
                .build();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> chatbotUserService.chat(request)
        );

        assertTrue(exception.getStatusCode().is5xxServerError());
    }

    @Test
    @DisplayName("Should throw BAD_REQUEST when user message is blank")
    void shouldThrowWhenMessageIsBlank() {
        when(chatbotUserConfig.isConfigured()).thenReturn(true);

        ChatbotUserRequest request = ChatbotUserRequest.builder()
                .message("   ")
                .history(List.of(new ChatbotUserMessageDto("user", "Previous question")))
                .build();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> chatbotUserService.chat(request)
        );

        assertTrue(exception.getStatusCode().is4xxClientError());
    }
}
