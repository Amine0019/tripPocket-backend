package tn.esprit.trippocketbackend.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.trippocketbackend.entities.Trip;
import tn.esprit.trippocketbackend.entities.TripStatus;
import tn.esprit.trippocketbackend.entities.User;
import tn.esprit.trippocketbackend.repositories.TripRepository;
import tn.esprit.trippocketbackend.repositories.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TripServiceTest {

    @Mock
    private TripRepository tripRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TripService tripService;

    private User sampleUser;
    private Trip sampleTrip;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(1L)
                .email("organizer@trippocket.com")
                .firstName("John")
                .lastName("Doe")
                .build();

        sampleTrip = Trip.builder()
                .id(100L)
                .title("Voyage au Japon")
                .destination("Tokyo")
                .price(BigDecimal.valueOf(1500.00))
                .startDate(LocalDate.of(2026, 7, 1))
                .endDate(LocalDate.of(2026, 7, 10))
                .status(TripStatus.PLANNED)
                .highlights(new ArrayList<>())
                .itinerary(new ArrayList<>())
                .user(sampleUser)
                .build();
    }

    // ───────────────── 1. CREATE ─────────────────

    @Test
    @DisplayName("Create Trip - Succès : calcule la durée et applique le statut par défaut")
    void testCreateTrip_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Trip newTrip = Trip.builder()
                .title("Voyage en Italie")
                .destination("Rome")
                .startDate(LocalDate.of(2026, 6, 1))
                .endDate(LocalDate.of(2026, 6, 10))
                .build();

        Trip created = tripService.createTrip(1L, newTrip, null);

        assertNotNull(created);
        assertEquals(TripStatus.PLANNED, created.getStatus());
        assertEquals(10, created.getDurationDays());
        assertEquals(sampleUser, created.getUser());
        verify(tripRepository, times(1)).save(newTrip);
    }

    @Test
    @DisplayName("Create Trip - Échec : date de fin antérieure à la date de début")
    void testCreateTrip_InvalidDates_ThrowsBadRequest() {
        Trip invalidTrip = Trip.builder()
                .title("Voyage Erreur")
                .startDate(LocalDate.of(2026, 6, 10))
                .endDate(LocalDate.of(2026, 6, 1))
                .build();

        assertThrows(ResponseStatusException.class, () -> tripService.createTrip(1L, invalidTrip, null));
        verify(tripRepository, never()).save(any());
    }

    // ───────────────── 2. READ ─────────────────

    @Test
    @DisplayName("Read All Trips - Succès : retourne la liste des voyages")
    void testGetAllTrips_Success() {
        when(tripRepository.findAll()).thenReturn(List.of(sampleTrip));

        List<Trip> trips = tripService.getAllTrips();

        assertNotNull(trips);
        assertEquals(1, trips.size());
        assertEquals("Voyage au Japon", trips.get(0).getTitle());
        verify(tripRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("Read Trip By ID - Succès : voyage trouvé")
    void testGetTripById_Success() {
        when(tripRepository.findById(100L)).thenReturn(Optional.of(sampleTrip));

        Trip found = tripService.getTripById(100L);

        assertNotNull(found);
        assertEquals(100L, found.getId());
        assertEquals("Voyage au Japon", found.getTitle());
        verify(tripRepository, times(1)).findById(100L);
    }

    @Test
    @DisplayName("Read Trip By ID - Échec : lève ResponseStatusException 404 si non trouvé")
    void testGetTripById_NotFound() {
        when(tripRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> tripService.getTripById(999L));
        verify(tripRepository, times(1)).findById(999L);
    }

    // ───────────────── 3. UPDATE ─────────────────

    @Test
    @DisplayName("Update Trip - Succès : mise à jour des champs")
    void testUpdateTrip_Success() {
        when(tripRepository.findById(100L)).thenReturn(Optional.of(sampleTrip));
        when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Trip updates = Trip.builder()
                .title("Voyage au Japon - Édition Cerisiers")
                .destination("Kyoto")
                .price(BigDecimal.valueOf(1800.00))
                .build();

        Trip updated = tripService.updateTrip(100L, updates, null);

        assertNotNull(updated);
        assertEquals("Voyage au Japon - Édition Cerisiers", updated.getTitle());
        assertEquals("Kyoto", updated.getDestination());
        assertEquals(BigDecimal.valueOf(1800.00), updated.getPrice());
        verify(tripRepository, times(1)).save(sampleTrip);
    }

    // ───────────────── 4. DELETE ─────────────────

    @Test
    @DisplayName("Delete Trip - Succès : suppression de l'entité")
    void testDeleteTrip_Success() {
        when(tripRepository.findById(100L)).thenReturn(Optional.of(sampleTrip));
        doNothing().when(tripRepository).delete(sampleTrip);

        assertDoesNotThrow(() -> tripService.deleteTrip(100L));
        verify(tripRepository, times(1)).delete(sampleTrip);
    }
}
