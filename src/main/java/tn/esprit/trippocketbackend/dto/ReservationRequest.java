package tn.esprit.trippocketbackend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import tn.esprit.trippocketbackend.entities.PaymentMethod;
import tn.esprit.trippocketbackend.entities.ReservationType;

import java.time.LocalDate;

/**
 * Body sent by the traveler to create or update a reservation.
 * The trip is given in the URL (?tripId=), the owner is the connected user.
 */
public record ReservationRequest(

        @NotNull(message = "Reservation type is required")
        ReservationType type,

        // Optional for TRIP (defaults to the trip start date), required for every other type
        LocalDate reservationDate,

        @NotNull(message = "Number of travelers is required")
        @Min(value = 1, message = "At least 1 traveler is required")
        @Max(value = 20, message = "At most 20 travelers per reservation")
        Integer travelers,

        @NotNull(message = "Payment method is required")
        PaymentMethod paymentMethod,

        // Ignored for TRIP: the server computes trip price x travelers
        @PositiveOrZero(message = "Price cannot be negative")
        Double price,

        @Size(max = 500, message = "Notes must be at most 500 characters")
        String notes
) {
}
