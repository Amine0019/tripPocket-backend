package tn.esprit.trippocketbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tn.esprit.trippocketbackend.dto.ChatbotUserMessageDto;
import tn.esprit.trippocketbackend.dto.ChatbotUserRequest;
import tn.esprit.trippocketbackend.dto.ChatbotUserResponse;
import tn.esprit.trippocketbackend.services.ChatbotUserService;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ChatbotUserControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ChatbotUserService chatbotUserService;

    @InjectMocks
    private ChatbotUserController chatbotUserController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(chatbotUserController).build();
    }

    @Test
    @DisplayName("POST /api/chatbot-user/chat - should return 200 with chatbot response")
    void chat_Success() throws Exception {
        ChatbotUserRequest request = ChatbotUserRequest.builder()
                .message("What are the best places to visit in Tunisia?")
                .history(List.of(new ChatbotUserMessageDto("user", "Hello")))
                .build();

        ChatbotUserResponse response = ChatbotUserResponse.builder()
                .reply("Tunisia has amazing destinations like Sidi Bou Said, Carthage, and the Sahara desert!")
                .timestamp(Instant.now().toString())
                .build();

        when(chatbotUserService.chat(any(ChatbotUserRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/chatbot-user/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(response.getReply()))
                .andExpect(jsonPath("$.timestamp").value(response.getTimestamp()));

        verify(chatbotUserService).chat(any(ChatbotUserRequest.class));
    }
}
