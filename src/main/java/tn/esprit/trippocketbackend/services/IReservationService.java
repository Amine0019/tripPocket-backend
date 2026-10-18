package tn.esprit.trippocketbackend.services;

import tn.esprit.trippocketbackend.dto.PageResponse;
import tn.esprit.trippocketbackend.dto.ReservationRequest;
import tn.esprit.trippocketbackend.dto.ReservationResponse;
import tn.esprit.trippocketbackend.dto.ReservationStatsResponse;
import tn.esprit.trippocketbackend.entities.ReservationStatus;
import tn.esprit.trippocketbackend.entities.ReservationType;

import java.util.List;

public interface IReservationService {

    // ── Traveler (connected user) ──────────────────────────────────────
    ReservationResponse createReservation(Long tripId, ReservationRequest request);

    List<ReservationResponse> getMyReservations(ReservationStatus status);

    ReservationResponse getReservationById(Long id);

    ReservationResponse updateReservation(Long id, ReservationRequest request);

    ReservationResponse cancelReservation(Long id);

    // ── Admin ──────────────────────────────────────────────────────────
    PageResponse<ReservationResponse> searchReservations(ReservationStatus status, ReservationType type,
                                                         Long tripId, String search, int page, int size);

    List<ReservationResponse> getReservationsByTripId(Long tripId);

    ReservationResponse updateStatus(Long id, ReservationStatus status);

    void deleteReservation(Long id);

    ReservationStatsResponse getStats();
}
