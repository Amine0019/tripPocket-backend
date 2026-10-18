package tn.esprit.trippocketbackend.services;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.trippocketbackend.dto.PageResponse;
import tn.esprit.trippocketbackend.dto.ReservationRequest;
import tn.esprit.trippocketbackend.dto.ReservationResponse;
import tn.esprit.trippocketbackend.dto.ReservationStatsResponse;
import tn.esprit.trippocketbackend.entities.Reservation;
import tn.esprit.trippocketbackend.entities.ReservationStatus;
import tn.esprit.trippocketbackend.entities.ReservationType;
import tn.esprit.trippocketbackend.entities.Trip;
import tn.esprit.trippocketbackend.entities.TripStatus;
import tn.esprit.trippocketbackend.entities.User;
import tn.esprit.trippocketbackend.repositories.ReservationRepository;
import tn.esprit.trippocketbackend.repositories.TripRepository;
import tn.esprit.trippocketbackend.repositories.UserRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReservationService implements IReservationService {

    private static final String CONFIRMATION_PREFIX = "TP-";
    private static final String CONFIRMATION_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CONFIRMATION_LENGTH = 8;
    private static final int MAX_CONFIRMATION_ATTEMPTS = 10;
    private static final int MAX_TRAVELERS = 20;
    private static final int MAX_NOTES_LENGTH = 500;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int TOP_TRIPS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    // PENDING -> CONFIRMED | CANCELLED ; CONFIRMED -> COMPLETED | CANCELLED ; CANCELLED and COMPLETED are final
    private static final Map<ReservationStatus, Set<ReservationStatus>> ALLOWED_TRANSITIONS = Map.of(
            ReservationStatus.PENDING, EnumSet.of(ReservationStatus.CONFIRMED, ReservationStatus.CANCELLED),
            ReservationStatus.CONFIRMED, EnumSet.of(ReservationStatus.COMPLETED, ReservationStatus.CANCELLED),
            ReservationStatus.CANCELLED, EnumSet.noneOf(ReservationStatus.class),
            ReservationStatus.COMPLETED, EnumSet.noneOf(ReservationStatus.class)
    );

    private final ReservationRepository reservationRepository;
    private final TripRepository tripRepository;
    private final UserRepository userRepository;

    // ───────────────────────── Traveler ─────────────────────────

    @Override
    @Transactional
    public ReservationResponse createReservation(Long tripId, ReservationRequest request) {
        User user = currentUser();
        Trip trip = findTrip(tripId);

        Reservation reservation = new Reservation();
        reservation.setTrip(trip);
        reservation.setUser(user);
        reservation.setStatus(ReservationStatus.PENDING);
        applyRequest(reservation, trip, request, null);
        reservation.setConfirmationNumber(generateConfirmationNumber());

        return ReservationResponse.from(reservationRepository.save(reservation));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservationResponse> getMyReservations(ReservationStatus status) {
        User user = currentUser();
        List<Reservation> reservations = (status == null)
                ? reservationRepository.findByUserIdOrderByCreatedAtDescIdDesc(user.getId())
                : reservationRepository.findByUserIdAndStatusOrderByCreatedAtDescIdDesc(user.getId(), status);
        return reservations.stream().map(ReservationResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationResponse getReservationById(Long id) {
        Reservation reservation = findReservation(id);
        assertOwnerOrAdmin(reservation, "view");
        return ReservationResponse.from(reservation);
    }

    @Override
    @Transactional
    public ReservationResponse updateReservation(Long id, ReservationRequest request) {
        Reservation reservation = findReservation(id);
        assertOwner(reservation, "modify");

        if (statusOf(reservation) != ReservationStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only pending reservations can be modified (this one is " + label(statusOf(reservation)) + ")");
        }
        if (reservation.getTrip() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This reservation is not linked to a trip");
        }

        applyRequest(reservation, reservation.getTrip(), request, reservation.getId());
        // saveAndFlush so that @PreUpdate runs now and the response carries the new updatedAt
        return ReservationResponse.from(reservationRepository.saveAndFlush(reservation));
    }

    @Override
    @Transactional
    public ReservationResponse cancelReservation(Long id) {
        Reservation reservation = findReservation(id);
        assertOwnerOrAdmin(reservation, "cancel");
        changeStatus(reservation, ReservationStatus.CANCELLED);
        return ReservationResponse.from(reservationRepository.saveAndFlush(reservation));
    }

    // ───────────────────────── Admin ─────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReservationResponse> searchReservations(ReservationStatus status, ReservationType type,
                                                                Long tripId, String search, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 10 : Math.min(size, MAX_PAGE_SIZE);
        String pattern = (search == null || search.isBlank())
                ? ""
                : "%" + search.trim().toLowerCase(Locale.ROOT) + "%";

        Pageable pageable = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        return PageResponse.from(reservationRepository
                .searchForAdmin(status, type, tripId, pattern, pageable)
                .map(ReservationResponse::from));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservationResponse> getReservationsByTripId(Long tripId) {
        findTrip(tripId);
        return reservationRepository.findByTripId(tripId).stream()
                .sorted(Comparator.comparing(Reservation::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(ReservationResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public ReservationResponse updateStatus(Long id, ReservationStatus status) {
        if (status == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is required");
        }
        Reservation reservation = findReservation(id);
        changeStatus(reservation, status);
        return ReservationResponse.from(reservationRepository.saveAndFlush(reservation));
    }

    @Override
    @Transactional
    public void deleteReservation(Long id) {
        reservationRepository.delete(findReservation(id));
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationStatsResponse getStats() {
        Map<ReservationStatus, Long> byStatus = new EnumMap<>(ReservationStatus.class);
        for (ReservationStatus s : ReservationStatus.values()) {
            byStatus.put(s, 0L);
        }
        for (Object[] row : reservationRepository.countGroupedByStatus()) {
            if (row[0] instanceof ReservationStatus s) {
                byStatus.put(s, toLong(row[1]));
            }
        }

        Map<ReservationType, Long> byType = new EnumMap<>(ReservationType.class);
        for (ReservationType t : ReservationType.values()) {
            byType.put(t, 0L);
        }
        for (Object[] row : reservationRepository.countGroupedByType()) {
            if (row[0] instanceof ReservationType t) {
                byType.put(t, toLong(row[1]));
            }
        }

        long total = reservationRepository.count();
        Double revenue = reservationRepository.sumPriceByStatusIn(
                List.of(ReservationStatus.CONFIRMED, ReservationStatus.COMPLETED));
        double cancellationRate = total == 0
                ? 0.0
                : round(byStatus.get(ReservationStatus.CANCELLED) * 100.0 / total, 1);

        List<ReservationStatsResponse.TopTrip> topTrips = reservationRepository
                .findTopTrips(ReservationStatus.CANCELLED, ReservationStatus.CONFIRMED, ReservationStatus.COMPLETED,
                        PageRequest.of(0, TOP_TRIPS))
                .stream()
                .map(row -> new ReservationStatsResponse.TopTrip(
                        row[0] == null ? null : toLong(row[0]),
                        row[1] != null ? (String) row[1] : (String) row[2],
                        (String) row[2],
                        toLong(row[3]),
                        toLong(row[4]),
                        round(toDouble(row[5]), 2)))
                .toList();

        return new ReservationStatsResponse(
                total,
                byStatus,
                byType,
                round(revenue == null ? 0.0 : revenue, 2),
                cancellationRate,
                topTrips);
    }

    // ───────────────────────── Business rules ─────────────────────────

    /** Validates the request against the trip and copies it into the reservation. */
    private void applyRequest(Reservation reservation, Trip trip, ReservationRequest request, Long existingId) {
        if (request == null || request.type() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reservation type is required");
        }
        if (request.paymentMethod() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment method is required");
        }
        Integer travelers = request.travelers();
        if (travelers == null || travelers < 1 || travelers > MAX_TRAVELERS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Number of travelers must be between 1 and " + MAX_TRAVELERS);
        }

        assertBookable(trip);
        LocalDate date = resolveDate(trip, request);
        if (request.type() == ReservationType.TRIP) {
            assertSpotsAvailable(trip, travelers, existingId);
        }

        reservation.setType(request.type());
        reservation.setReservationDate(date.atStartOfDay());
        reservation.setTravelers(travelers);
        reservation.setPaymentMethod(request.paymentMethod());
        reservation.setPrice(computePrice(trip, request.type(), travelers, request.price()));
        reservation.setNotes(cleanNotes(request.notes()));
    }

    /** A trip can be booked only while it is PLANNED (or has no status) and has not ended yet. */
    private void assertBookable(Trip trip) {
        if (trip.getStatus() != null && trip.getStatus() != TripStatus.PLANNED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This trip is " + trip.getStatus().name().toLowerCase(Locale.ROOT) + " and can no longer be booked");
        }
        if (trip.getEndDate() != null && trip.getEndDate().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This trip has already ended and can no longer be booked");
        }
    }

    /** TRIP defaults to the trip start date; any date must stay inside the trip dates. */
    private LocalDate resolveDate(Trip trip, ReservationRequest request) {
        LocalDate date = request.reservationDate();
        if (date == null) {
            if (request.type() == ReservationType.TRIP && trip.getStartDate() != null) {
                date = trip.getStartDate();
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reservation date is required");
            }
        }

        LocalDate start = trip.getStartDate();
        LocalDate end = trip.getEndDate();
        boolean beforeStart = start != null && date.isBefore(start);
        boolean afterEnd = end != null && date.isAfter(end);
        if (beforeStart || afterEnd) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Reservation date must be within the trip dates ("
                            + (start == null ? "?" : start) + " to " + (end == null ? "?" : end) + ")");
        }
        return date;
    }

    /** When the trip has a group size, TRIP bookings (not cancelled) cannot exceed it. */
    private void assertSpotsAvailable(Trip trip, int travelers, Long existingId) {
        Integer groupSize = trip.getGroupSize();
        if (groupSize == null || groupSize <= 0) {
            return;
        }

        Long alreadyBooked = reservationRepository.sumTravelersForTrip(
                trip.getId(), ReservationType.TRIP, ReservationStatus.CANCELLED,
                existingId == null ? -1L : existingId);
        long spotsLeft = Math.max(0, groupSize - (alreadyBooked == null ? 0 : alreadyBooked));

        if (travelers > spotsLeft) {
            String message = spotsLeft == 0
                    ? "This trip is fully booked"
                    : "Only " + spotsLeft + (spotsLeft == 1 ? " spot" : " spots") + " left on this trip";
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
        }
    }

    /** TRIP price is always computed by the server; other types use the price sent by the client (>= 0). */
    private Double computePrice(Trip trip, ReservationType type, int travelers, Double requestedPrice) {
        if (type == ReservationType.TRIP) {
            if (trip.getPrice() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "This trip has no price yet and cannot be booked");
            }
            return trip.getPrice()
                    .multiply(BigDecimal.valueOf(travelers))
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        if (requestedPrice == null) {
            return 0.0;
        }
        if (requestedPrice < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Price cannot be negative");
        }
        return round(requestedPrice, 2);
    }

    private void changeStatus(Reservation reservation, ReservationStatus target) {
        ReservationStatus current = statusOf(reservation);
        if (current == target) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This reservation is already " + label(target));
        }
        if (ALLOWED_TRANSITIONS.get(current).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A " + label(current) + " reservation can no longer be changed");
        }
        if (!ALLOWED_TRANSITIONS.get(current).contains(target)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A " + label(current) + " reservation cannot become " + label(target));
        }
        reservation.setStatus(target);
    }

    private String generateConfirmationNumber() {
        for (int attempt = 0; attempt < MAX_CONFIRMATION_ATTEMPTS; attempt++) {
            StringBuilder code = new StringBuilder(CONFIRMATION_PREFIX);
            for (int i = 0; i < CONFIRMATION_LENGTH; i++) {
                code.append(CONFIRMATION_CHARS.charAt(RANDOM.nextInt(CONFIRMATION_CHARS.length())));
            }
            String candidate = code.toString();
            if (!reservationRepository.existsByConfirmationNumber(candidate)) {
                return candidate;
            }
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Could not generate a confirmation number, please retry");
    }

    private String cleanNotes(String notes) {
        if (notes == null || notes.isBlank()) {
            return null;
        }
        String trimmed = notes.trim();
        if (trimmed.length() > MAX_NOTES_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Notes must be at most " + MAX_NOTES_LENGTH + " characters");
        }
        return trimmed;
    }

    // ───────────────────────── Security helpers ─────────────────────────

    private void assertOwnerOrAdmin(Reservation reservation, String action) {
        if (isAdmin()) {
            return;
        }
        assertOwner(reservation, action);
    }

    private void assertOwner(Reservation reservation, String action) {
        User user = currentUser();
        if (reservation.getUser() == null || !Objects.equals(reservation.getUser().getId(), user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You can only " + action + " your own reservations");
        }
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if ("ROLE_ADMIN".equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken
                || auth.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Your account was not found, please log in again"));
    }

    // ───────────────────────── Small helpers ─────────────────────────

    private Trip findTrip(Long tripId) {
        if (tripId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trip id is required");
        }
        return tripRepository.findById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
    }

    private Reservation findReservation(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found"));
    }

    private static ReservationStatus statusOf(Reservation reservation) {
        // Old rows created before this module may have no status: treat them as PENDING
        return reservation.getStatus() == null ? ReservationStatus.PENDING : reservation.getStatus();
    }

    private static String label(ReservationStatus status) {
        return status.name().toLowerCase(Locale.ROOT);
    }

    private static long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static double toDouble(Object value) {
        return value == null ? 0.0 : ((Number) value).doubleValue();
    }

    private static double round(double value, int decimals) {
        return BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).doubleValue();
    }
}
