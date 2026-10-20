package tn.esprit.trippocketbackend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatbotUserRequest {

    @NotBlank(message = "Message cannot be empty")
    @Size(max = 2000, message = "Message is too long (maximum 2000 characters)")
    private String message;

    @Builder.Default
    private List<@Valid ChatbotUserMessageDto> history = new ArrayList<>();
}
