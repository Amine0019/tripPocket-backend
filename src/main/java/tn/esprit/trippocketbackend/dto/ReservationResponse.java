package tn.esprit.trippocketbackend.dto;

import tn.esprit.trippocketbackend.entities.PaymentMethod;
import tn.esprit.trippocketbackend.entities.Reservation;
import tn.esprit.trippocketbackend.entities.ReservationStatus;
import tn.esprit.trippocketbackend.entities.ReservationType;
import tn.esprit.trippocketbackend.entities.Trip;
import tn.esprit.trippocketbackend.entities.User;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * What the API returns for a reservation: the reservation itself plus a small summary
 * of its trip and owner (never the password or other private user data).
 */
public record ReservationResponse(
        Long id,
        String confirmationNumber,
        ReservationType type,
        ReservationStatus status,
        LocalDate reservationDate,
        Integer travelers,
        Double price,
        PaymentMethod paymentMethod,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long tripId,
        String tripTitle,
        String tripDestination,
        LocalDate tripStartDate,
        LocalDate tripEndDate,
        Long userId,
        String userFullName,
        String userEmail
) {

    public static ReservationResponse from(Reservation reservation) {
        Trip trip = reservation.getTrip();
        User user = reservation.getUser();

        String tripTitle = null;
        if (trip != null) {
            tripTitle = (trip.getTitle() != null && !trip.getTitle().isBlank()) ? trip.getTitle() : trip.getDestination();
        }

        String fullName = null;
        if (user != null) {
            fullName = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                    + (user.getLastName() == null ? "" : user.getLastName())).trim();
        }

        return new ReservationResponse(
                reservation.getId(),
                reservation.getConfirmationNumber(),
                reservation.getType(),
                reservation.getStatus(),
                reservation.getReservationDate() == null ? null : reservation.getReservationDate().toLocalDate(),
                reservation.getTravelers(),
                reservation.getPrice(),
                reservation.getPaymentMethod(),
                reservation.getNotes(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt(),
                trip == null ? null : trip.getId(),
                tripTitle,
                trip == null ? null : trip.getDestination(),
                trip == null ? null : trip.getStartDate(),
                trip == null ? null : trip.getEndDate(),
                user == null ? null : user.getId(),
                fullName,
                user == null ? null : user.getEmail()
        );
    }
}
