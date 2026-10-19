package tn.esprit.trippocketbackend.dto;

import tn.esprit.trippocketbackend.entities.ReservationType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Result of "Analyze my trip with AI" for one trip and the connected user's bookings.
 * findings come from rules checked in Java (always present); summary, missing, checklist and warnings
 * come from the AI when aiAvailable is true, otherwise from the same rules.
 */
public record TripCheckResponse(
        Long tripId,
        String tripTitle,
        boolean aiAvailable,
        int readinessScore,
        String summary,
        List<Suggestion> missing,
        List<String> checklist,
        List<String> warnings,
        List<Finding> findings,
        LocalDateTime generatedAt
) {

    /** A booking the traveler may still need. Prices are AI estimates in USD for the whole group. */
    public record Suggestion(
            Integer dayNumber,
            ReservationType type,
            String title,
            String reason,
            LocalDate suggestedDate,
            Double estimatedPriceMin,
            Double estimatedPriceMax,
            String priority
    ) {
    }

    /** A rule checked in Java. severity is HIGH, MEDIUM or LOW. */
    public record Finding(String code, String severity, String message) {
    }
}
