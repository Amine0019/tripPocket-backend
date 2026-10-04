package tn.esprit.trippocketbackend.services;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.trippocketbackend.entities.Reservation;
import tn.esprit.trippocketbackend.entities.ReservationStatus;
import tn.esprit.trippocketbackend.repositories.ReservationRepository;
import tn.esprit.trippocketbackend.repositories.TripRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservationService implements IReservationService {

    private final ReservationRepository reservationRepository;
    private final TripRepository tripRepository;

    @Override
    public Reservation createReservation(Long tripId, Reservation reservation) {
        validatePrice(reservation.getPrice());

        reservation.setTrip(tripRepository.findById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found")));

        if (reservation.getStatus() == null) {
            reservation.setStatus(ReservationStatus.PENDING);
        }

        return reservationRepository.save(reservation);
    }

    @Override
    public List<Reservation> getAllReservations() {
        return reservationRepository.findAll();
    }

    @Override
    public List<Reservation> getReservationsByTripId(Long tripId) {
        return reservationRepository.findByTripId(tripId);
    }

    @Override
    public Reservation getReservationById(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found"));
    }

    @Override
    public Reservation updateReservation(Long id, Reservation reservation) {
        validatePrice(reservation.getPrice());

        Reservation existingReservation = getReservationById(id);
        existingReservation.setType(reservation.getType());
        existingReservation.setReservationDate(reservation.getReservationDate());
        existingReservation.setConfirmationNumber(reservation.getConfirmationNumber());
        existingReservation.setPrice(reservation.getPrice());
        existingReservation.setNotes(reservation.getNotes());
        existingReservation.setStatus(reservation.getStatus());

        return reservationRepository.save(existingReservation);
    }

    @Override
    public Reservation updateStatus(Long id, ReservationStatus status) {
        Reservation reservation = getReservationById(id);
        reservation.setStatus(status);
        return reservationRepository.save(reservation);
    }

    @Override
    public void deleteReservation(Long id) {
        Reservation reservation = getReservationById(id);
        reservationRepository.delete(reservation);
    }

    private void validatePrice(Double price) {
        if (price != null && price < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Price cannot be negative");
        }
    }
}
