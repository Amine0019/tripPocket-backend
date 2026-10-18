package tn.esprit.trippocketbackend.dto;

import tn.esprit.trippocketbackend.entities.PaymentMethod;
import tn.esprit.trippocketbackend.entities.ReservationType;

import java.time.LocalDate;

/**
 * Booking form values guessed from one sentence. Every field may be null when it was not understood.
 * Nothing is saved: the traveler checks the pre-filled form and confirms it.
 */
public record ReservationAiParseResponse(
        boolean aiAvailable,
        ReservationType type,
        Integer travelers,
        LocalDate reservationDate,
        PaymentMethod paymentMethod,
        Double price,
        String notes,
        double confidence,
        String message
) {
}
