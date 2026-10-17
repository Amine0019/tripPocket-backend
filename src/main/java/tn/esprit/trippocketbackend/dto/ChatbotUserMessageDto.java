package tn.esprit.trippocketbackend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatbotUserMessageDto {

    @NotBlank(message = "Role must not be blank")
    private String role; // "user" or "model"

    @NotBlank(message = "Content must not be blank")
    private String content;
}
