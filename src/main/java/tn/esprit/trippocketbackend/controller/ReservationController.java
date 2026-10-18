package tn.esprit.trippocketbackend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.trippocketbackend.dto.PageResponse;
import tn.esprit.trippocketbackend.dto.ReservationRequest;
import tn.esprit.trippocketbackend.dto.ReservationResponse;
import tn.esprit.trippocketbackend.dto.ReservationStatsResponse;
import tn.esprit.trippocketbackend.entities.ReservationStatus;
import tn.esprit.trippocketbackend.entities.ReservationType;
import tn.esprit.trippocketbackend.services.IReservationService;

import java.util.List;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
@Tag(name = "Reservations")
public class ReservationController {

    private final IReservationService reservationService;

    // ───────────────────────── Traveler (connected user) ─────────────────────────

    @PostMapping
    @Operation(summary = "Book a trip (or a service of a trip) for the connected user")
    public ResponseEntity<ReservationResponse> createReservation(
            @RequestParam Long tripId,
            @Valid @RequestBody ReservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservationService.createReservation(tripId, request));
    }

    @GetMapping("/me")
    @Operation(summary = "Get my reservations (optional status filter)")
    public ResponseEntity<List<ReservationResponse>> getMyReservations(
            @RequestParam(required = false) ReservationStatus status) {
        return ResponseEntity.ok(reservationService.getMyReservations(status));
    }

    @GetMapping("/{id:\\d+}")
    @Operation(summary = "Get a reservation by ID (owner or admin)")
    public ResponseEntity<ReservationResponse> getReservationById(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.getReservationById(id));
    }

    @PutMapping("/{id:\\d+}")
    @Operation(summary = "Modify one of my reservations (only while PENDING)")
    public ResponseEntity<ReservationResponse> updateReservation(
            @PathVariable Long id,
            @Valid @RequestBody ReservationRequest request) {
        return ResponseEntity.ok(reservationService.updateReservation(id, request));
    }

    @PatchMapping("/{id:\\d+}/cancel")
    @Operation(summary = "Cancel a reservation (owner or admin, PENDING or CONFIRMED only)")
    public ResponseEntity<ReservationResponse> cancelReservation(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.cancelReservation(id));
    }

    // ───────────────────────── Admin ─────────────────────────

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Search all reservations, paginated (admin only)")
    public ResponseEntity<PageResponse<ReservationResponse>> searchReservations(
            @RequestParam(required = false) ReservationStatus status,
            @RequestParam(required = false) ReservationType type,
            @RequestParam(required = false) Long tripId,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(reservationService.searchReservations(status, type, tripId, search, page, size));
    }

    @GetMapping("/trip/{tripId:\\d+}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get the reservations of a trip (admin only)")
    public ResponseEntity<List<ReservationResponse>> getReservationsByTripId(@PathVariable Long tripId) {
        return ResponseEntity.ok(reservationService.getReservationsByTripId(tripId));
    }

    @PatchMapping("/{id:\\d+}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Change a reservation status (admin only)")
    public ResponseEntity<ReservationResponse> updateStatus(
            @PathVariable Long id,
            @RequestParam ReservationStatus status) {
        return ResponseEntity.ok(reservationService.updateStatus(id, status));
    }

    @DeleteMapping("/{id:\\d+}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a reservation (admin only)")
    public ResponseEntity<Void> deleteReservation(@PathVariable Long id) {
        reservationService.deleteReservation(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reservation statistics for the admin dashboard (admin only)")
    public ResponseEntity<ReservationStatsResponse> getStats() {
        return ResponseEntity.ok(reservationService.getStats());
    }
}
