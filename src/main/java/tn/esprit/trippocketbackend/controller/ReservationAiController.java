package tn.esprit.trippocketbackend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.trippocketbackend.dto.ReservationAiInsightsResponse;
import tn.esprit.trippocketbackend.dto.ReservationAiParseRequest;
import tn.esprit.trippocketbackend.dto.ReservationAiParseResponse;
import tn.esprit.trippocketbackend.dto.TripCheckResponse;
import tn.esprit.trippocketbackend.services.IReservationAiService;

/**
 * Reservation AI assistant. Kept under /api/reservations/ai (not /api/ai, which is admin-only)
 * so travelers can use the first two features.
 */
@RestController
@RequestMapping("/api/reservations/ai")
@RequiredArgsConstructor
@Tag(name = "Reservation AI assistant")
public class ReservationAiController {

    private final IReservationAiService reservationAiService;

    @PostMapping("/trip-check")
    @Operation(summary = "Check my bookings for a trip and suggest what is missing")
    public ResponseEntity<TripCheckResponse> checkTrip(@RequestParam Long tripId) {
        return ResponseEntity.ok(reservationAiService.checkTrip(tripId));
    }

    @PostMapping("/parse-request")
    @Operation(summary = "Turn one sentence into booking form values (nothing is saved)")
    public ResponseEntity<ReservationAiParseResponse> parseRequest(@Valid @RequestBody ReservationAiParseRequest request) {
        return ResponseEntity.ok(reservationAiService.parseRequest(request));
    }

    @PostMapping("/admin-insights")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "AI summary, alerts and recommended actions for the reservations dashboard (admin only)")
    public ResponseEntity<ReservationAiInsightsResponse> adminInsights() {
        return ResponseEntity.ok(reservationAiService.adminInsights());
    }
}
