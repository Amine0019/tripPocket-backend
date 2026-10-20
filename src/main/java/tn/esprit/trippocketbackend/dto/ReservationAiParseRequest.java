package tn.esprit.trippocketbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** "Describe your booking in one sentence": the sentence and the trip it is about. */
public record ReservationAiParseRequest(
        @NotNull(message = "Trip id is required")
        Long tripId,

        @NotBlank(message = "Please describe your booking")
        @Size(max = 300, message = "Keep it under 300 characters")
        String text
) {
}
