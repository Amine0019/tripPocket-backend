package tn.esprit.trippocketbackend.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI insights for the admin reservations dashboard. Alerts are computed in Java from the real numbers;
 * the AI (when available) writes the summary, the highlights and the recommended actions.
 */
public record ReservationAiInsightsResponse(
        boolean aiAvailable,
        String summary,
        List<String> highlights,
        List<Alert> alerts,
        List<String> recommendations,
        LocalDateTime generatedAt
) {

    /** severity is HIGH, MEDIUM or LOW; tripId is set when the alert is about one trip. */
    public record Alert(String severity, String title, String detail, Long tripId) {
    }
}
