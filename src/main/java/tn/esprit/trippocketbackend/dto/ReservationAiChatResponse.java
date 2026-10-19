package tn.esprit.trippocketbackend.dto;

import java.util.List;

/**
 * Booking assistant answer.
 *
 * @param aiAvailable false when the answer was built with simple rules (no API key or AI error)
 * @param reply       the assistant's message
 * @param booking     all the fields collected so far (the form is pre-filled with them)
 * @param missing     fields still needed: type, travelers, reservationDate, paymentMethod
 * @param ready       true when the booking can be reviewed and confirmed in the form
 * @param suggestions short quick replies for the next answer
 * @param spotsLeft   spots left on the trip package (null = no limit)
 * @param warning     a problem that blocks this booking (closed trip, not enough spots...), or null
 */
public record ReservationAiChatResponse(
        boolean aiAvailable,
        String reply,
        ReservationAiChatRequest.BookingState booking,
        List<String> missing,
        boolean ready,
        List<String> suggestions,
        Integer spotsLeft,
        String warning
) {
}
