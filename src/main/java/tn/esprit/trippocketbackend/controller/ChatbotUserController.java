package tn.esprit.trippocketbackend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.trippocketbackend.dto.ChatbotUserRequest;
import tn.esprit.trippocketbackend.dto.ChatbotUserResponse;
import tn.esprit.trippocketbackend.services.ChatbotUserService;

@RestController
@RequestMapping("/api/chatbot-user")
@RequiredArgsConstructor
@Tag(name = "Chatbot User", description = "AI conversational assistant powered by Google Gemini for frontoffice travelers")
public class ChatbotUserController {

    private final ChatbotUserService chatbotUserService;

    @PostMapping("/chat")
    @Operation(summary = "Send a message to the Gemini chatbot and receive a contextual reply")
    public ResponseEntity<ChatbotUserResponse> chat(@Valid @RequestBody ChatbotUserRequest request) {
        ChatbotUserResponse response = chatbotUserService.chat(request);
        return ResponseEntity.ok(response);
    }
}
