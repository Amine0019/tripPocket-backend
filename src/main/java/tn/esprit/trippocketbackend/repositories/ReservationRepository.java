package tn.esprit.trippocketbackend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.trippocketbackend.entities.Reservation;
import tn.esprit.trippocketbackend.entities.ReservationStatus;

import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByTripId(Long tripId);

    List<Reservation> findByTripIdAndStatus(Long tripId, ReservationStatus status);
}
