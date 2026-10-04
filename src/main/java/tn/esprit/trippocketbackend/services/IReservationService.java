package tn.esprit.trippocketbackend.services;

import tn.esprit.trippocketbackend.entities.Reservation;
import tn.esprit.trippocketbackend.entities.ReservationStatus;

import java.util.List;

public interface IReservationService {

    Reservation createReservation(Long tripId, Reservation reservation);

    List<Reservation> getAllReservations();

    List<Reservation> getReservationsByTripId(Long tripId);

    Reservation getReservationById(Long id);

    Reservation updateReservation(Long id, Reservation reservation);

    Reservation updateStatus(Long id, ReservationStatus status);

    void deleteReservation(Long id);
}
