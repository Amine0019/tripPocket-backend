package tn.esprit.trippocketbackend.services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.trippocketbackend.dto.ReservationRequest;
import tn.esprit.trippocketbackend.dto.ReservationResponse;
import tn.esprit.trippocketbackend.entities.PaymentMethod;
import tn.esprit.trippocketbackend.entities.Reservation;
import tn.esprit.trippocketbackend.entities.ReservationStatus;
import tn.esprit.trippocketbackend.entities.ReservationType;
import tn.esprit.trippocketbackend.entities.Role;
import tn.esprit.trippocketbackend.entities.Trip;
import tn.esprit.trippocketbackend.entities.TripStatus;
import tn.esprit.trippocketbackend.entities.User;
import tn.esprit.trippocketbackend.repositories.ReservationRepository;
import tn.esprit.trippocketbackend.repositories.TripRepository;
import tn.esprit.trippocketbackend.repositories.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private TripRepository tripRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ReservationService reservationService;

    private User traveler;
    private User otherTraveler;
    private Trip trip;
    private LocalDate start;

    @BeforeEach
    void setUp() {
        traveler = User.builder().id(1L).email("traveler@example.com")
                .firstName("John").lastName("Doe").role(Role.TRAVELER).enabled(true).build();
        otherTraveler = User.builder().id(2L).email("other@example.com")
                .firstName("Jane").lastName("Roe").role(Role.TRAVELER).enabled(true).build();

        start = LocalDate.now().plusDays(30);
        trip = Trip.builder()
                .id(10L)
                .title("Discover Japan")
                .destination("Japan")
                .startDate(start)
                .endDate(start.plusDays(7))
                .price(new BigDecimal("1290.00"))
                .groupSize(12)
                .status(TripStatus.PLANNED)
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ───────────────────────── helpers ─────────────────────────

    private void loginAs(String email, String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority(role))));
    }

    private void loginAsTraveler(User user) {
        loginAs(user.getEmail(), "ROLE_TRAVELER");
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    private ReservationRequest tripRequest(int travelers, Double clientPrice) {
        return new ReservationRequest(ReservationType.TRIP, null, travelers, PaymentMethod.CREDIT_CARD,
                clientPrice, "  Window seat please  ");
    }

    private Reservation existingReservation(Long id, User owner, ReservationStatus status) {
        return Reservation.builder()
                .id(id)
                .type(ReservationType.TRIP)
                .status(status)
                .travelers(2)
                .price(2580.0)
                .paymentMethod(PaymentMethod.CASH)
                .reservationDate(start.atStartOfDay())
                .confirmationNumber("TP-ABCD1234")
                .trip(trip)
                .user(owner)
                .build();
    }

    // ───────────────────────── creation ─────────────────────────

    @Test
    @DisplayName("createReservation - should create a PENDING TRIP reservation for the connected user")
    void createReservation_Success() {
        loginAsTraveler(traveler);
        when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
        when(reservationRepository.sumTravelersForTrip(10L, ReservationType.TRIP, ReservationStatus.CANCELLED, -1L))
                .thenReturn(4L);
        when(reservationRepository.existsByConfirmationNumber(anyString())).thenReturn(false);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation saved = invocation.getArgument(0);
            saved.setId(100L);
            return saved;
        });

        ReservationResponse result = reservationService.createReservation(10L, tripRequest(2, null));

        assertEquals(100L, result.id());
        assertEquals(ReservationStatus.PENDING, result.status());
        assertEquals(ReservationType.TRIP, result.type());
        assertEquals(start, result.reservationDate(), "TRIP date defaults to the trip start date");
        assertEquals(traveler.getId(), result.userId());
        assertEquals(10L, result.tripId());
        assertEquals("Window seat please", result.notes());
        assertTrue(result.confirmationNumber().matches("TP-[A-Z0-9]{8}"),
                "Unexpected confirmation number: " + result.confirmationNumber());
        verify(reservationRepository).save(any(Reservation.class));
    }

    @Test
    @DisplayName("createReservation - TRIP price is trip price x travelers, the client price is ignored")
    void createReservation_TripPriceComputedByServer() {
        loginAsTraveler(traveler);
        when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
        when(reservationRepository.sumTravelersForTrip(anyLong(), any(), any(), anyLong())).thenReturn(0L);
        when(reservationRepository.existsByConfirmationNumber(anyString())).thenReturn(false);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponse result = reservationService.createReservation(10L, tripRequest(3, 1.0));

        assertEquals(3870.0, result.price());
    }

    @Test
    @DisplayName("createReservation - other types keep the price sent by the client (rounded to 2 decimals)")
    void createReservation_HotelUsesClientPrice() {
        loginAsTraveler(traveler);
        when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
        when(reservationRepository.existsByConfirmationNumber(anyString())).thenReturn(false);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReservationRequest hotel = new ReservationRequest(ReservationType.HOTEL, start.plusDays(1), 2,
                PaymentMethod.ONLINE_PAYMENT, 199.999, null);
        ReservationResponse result = reservationService.createReservation(10L, hotel);

        assertEquals(200.0, result.price());
        assertEquals(start.plusDays(1), result.reservationDate());
        verify(reservationRepository, never()).sumTravelersForTrip(anyLong(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("createReservation - should throw CONFLICT when not enough spots are left")
    void createReservation_NotEnoughSpots() {
        loginAsTraveler(traveler);
        when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
        when(reservationRepository.sumTravelersForTrip(10L, ReservationType.TRIP, ReservationStatus.CANCELLED, -1L))
                .thenReturn(11L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                reservationService.createReservation(10L, tripRequest(2, null)));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("Only 1 spot left on this trip", ex.getReason());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("createReservation - should throw NOT_FOUND when the trip does not exist")
    void createReservation_TripNotFound() {
        loginAsTraveler(traveler);
        when(tripRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                reservationService.createReservation(99L, tripRequest(1, null)));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("createReservation - should throw BAD_REQUEST when the trip is COMPLETED")
    void createReservation_TripCompleted() {
        loginAsTraveler(traveler);
        trip.setStatus(TripStatus.COMPLETED);
        when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                reservationService.createReservation(10L, tripRequest(1, null)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("createReservation - should throw BAD_REQUEST when the date is outside the trip dates")
    void createReservation_DateOutsideTrip() {
        loginAsTraveler(traveler);
        when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));

        ReservationRequest hotel = new ReservationRequest(ReservationType.HOTEL, start.plusDays(10), 2,
                PaymentMethod.CASH, 120.0, null);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                reservationService.createReservation(10L, hotel));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().startsWith("Reservation date must be within the trip dates"));
        verify(reservationRepository, never()).save(any());
    }

    // ───────────────────────── ownership ─────────────────────────

    @Test
    @DisplayName("getReservationById - should throw FORBIDDEN for another traveler's reservation")
    void getReservationById_OtherUserForbidden() {
        loginAsTraveler(otherTraveler);
        when(reservationRepository.findById(5L))
                .thenReturn(Optional.of(existingReservation(5L, traveler, ReservationStatus.PENDING)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                reservationService.getReservationById(5L));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    @DisplayName("getReservationById - an ADMIN can read any reservation")
    void getReservationById_AdminAllowed() {
        loginAs("admin@example.com", "ROLE_ADMIN");
        when(reservationRepository.findById(5L))
                .thenReturn(Optional.of(existingReservation(5L, traveler, ReservationStatus.PENDING)));

        ReservationResponse result = reservationService.getReservationById(5L);

        assertEquals(5L, result.id());
        assertEquals("traveler@example.com", result.userEmail());
        assertEquals("John Doe", result.userFullName());
    }

    // ───────────────────────── status rules ─────────────────────────

    @Test
    @DisplayName("updateStatus - should throw CONFLICT for a forbidden transition (CANCELLED -> CONFIRMED)")
    void updateStatus_InvalidTransition() {
        when(reservationRepository.findById(5L))
                .thenReturn(Optional.of(existingReservation(5L, traveler, ReservationStatus.CANCELLED)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                reservationService.updateStatus(5L, ReservationStatus.CONFIRMED));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(reservationRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("updateStatus - PENDING -> CONFIRMED is allowed")
    void updateStatus_PendingToConfirmed() {
        when(reservationRepository.findById(5L))
                .thenReturn(Optional.of(existingReservation(5L, traveler, ReservationStatus.PENDING)));
        when(reservationRepository.saveAndFlush(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponse result = reservationService.updateStatus(5L, ReservationStatus.CONFIRMED);

        assertEquals(ReservationStatus.CONFIRMED, result.status());
    }

    @Test
    @DisplayName("cancelReservation - the owner can cancel a CONFIRMED reservation")
    void cancelReservation_Success() {
        loginAsTraveler(traveler);
        when(reservationRepository.findById(5L))
                .thenReturn(Optional.of(existingReservation(5L, traveler, ReservationStatus.CONFIRMED)));
        when(reservationRepository.saveAndFlush(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponse result = reservationService.cancelReservation(5L);

        assertEquals(ReservationStatus.CANCELLED, result.status());
        verify(reservationRepository).saveAndFlush(any(Reservation.class));
    }

    @Test
    @DisplayName("updateReservation - should throw CONFLICT when the reservation is not PENDING")
    void updateReservation_NotPending() {
        loginAsTraveler(traveler);
        when(reservationRepository.findById(5L))
                .thenReturn(Optional.of(existingReservation(5L, traveler, ReservationStatus.CONFIRMED)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                reservationService.updateReservation(5L, tripRequest(1, null)));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(reservationRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("updateReservation - the spots check ignores the reservation being modified")
    void updateReservation_SpotsExcludeCurrentReservation() {
        loginAsTraveler(traveler);
        when(reservationRepository.findById(5L))
                .thenReturn(Optional.of(existingReservation(5L, traveler, ReservationStatus.PENDING)));
        when(reservationRepository.sumTravelersForTrip(10L, ReservationType.TRIP, ReservationStatus.CANCELLED, 5L))
                .thenReturn(8L);
        when(reservationRepository.saveAndFlush(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponse result = reservationService.updateReservation(5L, tripRequest(4, null));

        assertEquals(4, result.travelers());
        assertEquals(5160.0, result.price());
        verify(reservationRepository).sumTravelersForTrip(eq(10L), eq(ReservationType.TRIP),
                eq(ReservationStatus.CANCELLED), eq(5L));
    }
}
