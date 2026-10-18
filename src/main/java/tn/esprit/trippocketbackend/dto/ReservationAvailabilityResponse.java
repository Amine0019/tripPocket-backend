package tn.esprit.trippocketbackend.dto;

/**
 * Booking status of a trip for the booking page.
 * groupSize and spotsLeft are null when the trip has no group size limit.
 * reason explains why bookable is false (null when bookable).
 */
public record ReservationAvailabilityResponse(
        Long tripId,
        Integer groupSize,
        long booked,
        Integer spotsLeft,
        boolean bookable,
        String reason
) {
}
