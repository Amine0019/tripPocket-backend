package tn.esprit.trippocketbackend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tn.esprit.trippocketbackend.entities.PaymentMethod;
import tn.esprit.trippocketbackend.entities.ReservationType;

import java.time.LocalDate;
import java.util.List;

/**
 * Booking assistant (chat): the conversation so far and the booking fields already collected.
 * Nothing is saved: the traveler still confirms the reservation in the form.
 */
public record ReservationAiChatRequest(
        @NotNull(message = "Trip id is required")
        Long tripId,

        @NotEmpty(message = "Write a message")
        @Size(max = 20, message = "The conversation is too long, please start a new one")
        List<@Valid Message> messages,

        BookingState booking
) {

    /** One chat message: written by the traveler ("user") or by the assistant. */
    public record Message(
            @NotBlank(message = "The message role is required")
            @Pattern(regexp = "user|assistant", message = "The role must be user or assistant")
            String role,

            @NotBlank(message = "The message is empty")
            @Size(max = 600, message = "Keep each message under 600 characters")
            String content
    ) {
    }

    /** The booking fields collected so far (all optional). */
    public record BookingState(
            ReservationType type,
            Integer travelers,
            LocalDate reservationDate,
            PaymentMethod paymentMethod,
            Double price,
            String notes
    ) {
        public static BookingState empty() {
            return new BookingState(null, null, null, null, null, null);
        }
    }
}
