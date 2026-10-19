package tn.esprit.trippocketbackend.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tn.esprit.trippocketbackend.entities.Reservation;
import tn.esprit.trippocketbackend.entities.ReservationStatus;
import tn.esprit.trippocketbackend.entities.ReservationType;

import java.util.Collection;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByTripId(Long tripId);

    List<Reservation> findByTripIdAndStatus(Long tripId, ReservationStatus status);

    // ── Traveler side ────────────────────────────────────────────────
    List<Reservation> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    List<Reservation> findByUserIdAndStatusOrderByCreatedAtDescIdDesc(Long userId, ReservationStatus status);

    boolean existsByConfirmationNumber(String confirmationNumber);

    List<Reservation> findByUserIdAndTripIdOrderByReservationDateAscIdAsc(Long userId, Long tripId);

    /** Every reservation with its trip (admin AI insights). */
    @Query("SELECT r FROM Reservation r LEFT JOIN FETCH r.trip")
    List<Reservation> findAllWithTrip();

    /**
     * Travelers already booked on a trip for a given reservation type, ignoring one status
     * (CANCELLED) and one reservation id (the one being modified, or -1 on creation).
     */
    @Query("""
            SELECT COALESCE(SUM(r.travelers), 0) FROM Reservation r
            WHERE r.trip.id = :tripId
              AND r.type = :type
              AND (r.status IS NULL OR r.status <> :excludedStatus)
              AND r.id <> :excludedId
            """)
    Long sumTravelersForTrip(@Param("tripId") Long tripId,
                             @Param("type") ReservationType type,
                             @Param("excludedStatus") ReservationStatus excludedStatus,
                             @Param("excludedId") Long excludedId);

    // ── Admin side ───────────────────────────────────────────────────
    @Query("""
            SELECT r FROM Reservation r
            LEFT JOIN r.trip t
            LEFT JOIN r.user u
            WHERE (:status IS NULL OR r.status = :status)
              AND (:type IS NULL OR r.type = :type)
              AND (:tripId IS NULL OR t.id = :tripId)
              AND (:search = ''
                   OR LOWER(r.confirmationNumber) LIKE :search
                   OR LOWER(t.title) LIKE :search
                   OR LOWER(t.destination) LIKE :search
                   OR LOWER(u.email) LIKE :search
                   OR LOWER(CONCAT(u.firstName, ' ', u.lastName)) LIKE :search)
            """)
    Page<Reservation> searchForAdmin(@Param("status") ReservationStatus status,
                                     @Param("type") ReservationType type,
                                     @Param("tripId") Long tripId,
                                     @Param("search") String search,
                                     Pageable pageable);

    @Query("SELECT r.status, COUNT(r) FROM Reservation r GROUP BY r.status")
    List<Object[]> countGroupedByStatus();

    @Query("SELECT r.type, COUNT(r) FROM Reservation r GROUP BY r.type")
    List<Object[]> countGroupedByType();

    @Query("SELECT SUM(r.price) FROM Reservation r WHERE r.status IN :statuses")
    Double sumPriceByStatusIn(@Param("statuses") Collection<ReservationStatus> statuses);

    /**
     * Rows: [tripId, title, destination, reservations, travelers, revenue (CONFIRMED + COMPLETED)].
     */
    @Query("""
            SELECT t.id, t.title, t.destination, COUNT(r), SUM(r.travelers),
                   SUM(CASE WHEN r.status = :confirmed OR r.status = :completed THEN r.price ELSE 0.0 END)
            FROM Reservation r JOIN r.trip t
            WHERE r.status IS NULL OR r.status <> :excludedStatus
            GROUP BY t.id, t.title, t.destination
            ORDER BY COUNT(r) DESC, t.id ASC
            """)
    List<Object[]> findTopTrips(@Param("excludedStatus") ReservationStatus excludedStatus,
                                @Param("confirmed") ReservationStatus confirmed,
                                @Param("completed") ReservationStatus completed,
                                Pageable pageable);
}
