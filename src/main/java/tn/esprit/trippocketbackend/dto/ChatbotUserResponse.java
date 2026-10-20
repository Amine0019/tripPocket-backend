package tn.esprit.trippocketbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatbotUserResponse {

    private String reply;

    @Builder.Default
    private String timestamp = Instant.now().toString();
}
