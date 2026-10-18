package tn.esprit.trippocketbackend.dto;

import tn.esprit.trippocketbackend.entities.ReservationStatus;
import tn.esprit.trippocketbackend.entities.ReservationType;

import java.util.List;
import java.util.Map;

/**
 * Admin dashboard numbers for reservations.
 * revenue = sum of prices of CONFIRMED + COMPLETED reservations.
 * cancellationRate = % of reservations that are CANCELLED (0 to 100, one decimal).
 */
public record ReservationStatsResponse(
        long total,
        Map<ReservationStatus, Long> countByStatus,
        Map<ReservationType, Long> countByType,
        double revenue,
        double cancellationRate,
        List<TopTrip> topTrips
) {

    public record TopTrip(
            Long tripId,
            String tripTitle,
            String tripDestination,
            long reservations,
            long travelers,
            double revenue
    ) {
    }
}
