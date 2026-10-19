package tn.esprit.trippocketbackend.services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import tn.esprit.trippocketbackend.dto.ReservationAiChatRequest;
import tn.esprit.trippocketbackend.dto.ReservationAiChatRequest.BookingState;
import tn.esprit.trippocketbackend.dto.ReservationAiChatRequest.Message;
import tn.esprit.trippocketbackend.dto.ReservationAiChatResponse;
import tn.esprit.trippocketbackend.dto.ReservationAiInsightsResponse;
import tn.esprit.trippocketbackend.dto.ReservationAiParseRequest;
import tn.esprit.trippocketbackend.dto.ReservationAiParseResponse;
import tn.esprit.trippocketbackend.dto.TripCheckResponse;
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
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationAiServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private TripRepository tripRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReservationLlmClient llmClient;

    private ReservationAiService service;
    private User traveler;
    private Trip trip;
    private LocalDate start;

    @BeforeEach
    void setUp() {
        service = new ReservationAiService(reservationRepository, tripRepository, userRepository, llmClient,
                JsonMapper.builder().build());
        traveler = User.builder().id(1L).email("traveler@example.com").firstName("John").lastName("Doe")
                .role(Role.TRAVELER).enabled(true).build();
        start = LocalDate.now().plusDays(30);
        trip = Trip.builder().id(10L).title("Sahara Escape").destination("Tunisia")
                .startDate(start).endDate(start.plusDays(4)).durationDays(5)
                .price(new BigDecimal("450")).groupSize(4).status(TripStatus.PLANNED).build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ───────────────────────── helpers ─────────────────────────

    private void loginAsTraveler() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                traveler.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_TRAVELER"))));
        when(userRepository.findByEmail(traveler.getEmail())).thenReturn(Optional.of(traveler));
    }

    private Reservation booking(Long id, ReservationType type, LocalDate date, ReservationStatus status) {
        return Reservation.builder().id(id).type(type).reservationDate(date.atStartOfDay()).status(status)
                .travelers(2).price(100.0).paymentMethod(PaymentMethod.CASH).trip(trip).user(traveler).build();
    }

    private void myBookings(Reservation... reservations) {
        when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
        when(reservationRepository.findByUserIdAndTripIdOrderByReservationDateAscIdAsc(1L, 10L))
                .thenReturn(List.of(reservations));
    }

    // ───────────────────────── trip check ─────────────────────────

    @Test
    @DisplayName("checkTrip - without API key, returns the Java checks only (degraded mode)")
    void checkTrip_NoApiKey_DegradedMode() {
        loginAsTraveler();
        myBookings(booking(1L, ReservationType.HOTEL, start, ReservationStatus.CONFIRMED));
        when(llmClient.isConfigured()).thenReturn(false);

        TripCheckResponse result = service.checkTrip(10L);

        assertFalse(result.aiAvailable());
        assertTrue(result.findings().stream().anyMatch(f -> f.code().equals("NO_TRIP_PACKAGE")));
        assertTrue(result.findings().stream().anyMatch(f -> f.code().equals("NIGHTS_WITHOUT_HOTEL")));
        assertEquals(ReservationType.TRIP, result.missing().get(0).type());
        assertTrue(result.readinessScore() >= 0 && result.readinessScore() <= 100);
        verify(llmClient, never()).complete(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("checkTrip - retries once when the AI returns invalid JSON")
    void checkTrip_RetriesOnInvalidJson() {
        loginAsTraveler();
        myBookings(booking(1L, ReservationType.TRIP, start, ReservationStatus.CONFIRMED));
        when(llmClient.isConfigured()).thenReturn(true);
        when(llmClient.complete(anyString(), anyString(), anyInt()))
                .thenReturn("Sorry, here is my answer without JSON")
                .thenReturn("```json\n{\"readinessScore\": 90, \"summary\": \"Almost ready.\", \"missing\": [], "
                        + "\"checklist\": [\"Pack sunscreen\"], \"warnings\": []}\n```");

        TripCheckResponse result = service.checkTrip(10L);

        assertTrue(result.aiAvailable());
        assertEquals("Almost ready.", result.summary());
        assertEquals(List.of("Pack sunscreen"), result.checklist());
        verify(llmClient, times(2)).complete(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("checkTrip - invalid types are dropped and dates outside the trip are rejected")
    void checkTrip_FiltersInvalidTypesAndDates() {
        loginAsTraveler();
        myBookings(booking(1L, ReservationType.TRIP, start, ReservationStatus.CONFIRMED));
        when(llmClient.isConfigured()).thenReturn(true);
        String outside = start.plusDays(40).toString();
        when(llmClient.complete(anyString(), anyString(), anyInt())).thenReturn("""
                {"readinessScore": 80, "summary": "Good.", "missing": [
                  {"type": "SPA", "title": "Spa day", "reason": "Relax", "priority": "LOW"},
                  {"type": "TRIP", "title": "Book the package", "reason": "Already booked", "priority": "HIGH"},
                  {"dayNumber": 2, "type": "RESTAURANT", "title": "Dinner in Douz", "reason": "Local food",
                   "suggestedDate": "%s", "estimatedPriceMin": 90, "estimatedPriceMax": 40, "priority": "urgent"}
                ], "checklist": [], "warnings": []}
                """.formatted(outside));

        TripCheckResponse result = service.checkTrip(10L);

        assertEquals(1, result.missing().size(), "SPA (unknown type) and TRIP (already booked) are removed");
        TripCheckResponse.Suggestion dinner = result.missing().get(0);
        assertEquals(ReservationType.RESTAURANT, dinner.type());
        assertEquals(start.plusDays(1), dinner.suggestedDate(), "date outside the trip replaced by day 2");
        assertEquals(40.0, dinner.estimatedPriceMin());
        assertEquals(90.0, dinner.estimatedPriceMax());
        assertEquals("MEDIUM", dinner.priority());
    }

    @Test
    @DisplayName("checkTrip - the AI score is kept between 0 and 100 and close to the rules score")
    void checkTrip_ScoreIsBounded() {
        loginAsTraveler();
        myBookings(booking(1L, ReservationType.HOTEL, start, ReservationStatus.CONFIRMED));
        when(llmClient.isConfigured()).thenReturn(true);
        when(llmClient.complete(anyString(), anyString(), anyInt()))
                .thenReturn("{\"readinessScore\": 250, \"summary\": \"Great\", \"missing\": []}")
                .thenReturn("{\"readinessScore\": -40, \"summary\": \"Bad\", \"missing\": []}");

        TripCheckResponse high = service.checkTrip(10L);
        TripCheckResponse low = service.checkTrip(10L);

        assertTrue(high.readinessScore() <= 100);
        assertTrue(low.readinessScore() >= 0);
        int baseline = service.baselineScore(high.findings(), false);
        assertEquals(Math.min(100, baseline + 25), high.readinessScore());
        assertEquals(Math.max(0, baseline - 25), low.readinessScore());
    }

    @Test
    @DisplayName("checkTrip - an AI failure falls back to the Java checks")
    void checkTrip_AiFailureFallsBack() {
        loginAsTraveler();
        myBookings(booking(1L, ReservationType.TRIP, start, ReservationStatus.PENDING));
        when(llmClient.isConfigured()).thenReturn(true);
        when(llmClient.complete(anyString(), anyString(), anyInt()))
                .thenThrow(new ReservationLlmClient.LlmException("HTTP 401"));

        TripCheckResponse result = service.checkTrip(10L);

        assertFalse(result.aiAvailable());
        assertNotNull(result.summary());
        verify(llmClient, times(1)).complete(anyString(), anyString(), anyInt());
    }

    // ───────────────────────── booking in one sentence ─────────────────────────

    @Test
    @DisplayName("parseRequest - without API key, reads the sentence with keywords")
    void parseRequest_NoApiKey_UsesKeywords() {
        loginAsTraveler();
        when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
        when(llmClient.isConfigured()).thenReturn(false);

        ReservationAiParseResponse result = service.parseRequest(
                new ReservationAiParseRequest(10L, "A hotel on day 2 for 3 people, around $150, paid by card"));

        assertFalse(result.aiAvailable());
        assertEquals(ReservationType.HOTEL, result.type());
        assertEquals(3, result.travelers());
        assertEquals(start.plusDays(1), result.reservationDate());
        assertEquals(150.0, result.price());
        assertEquals(PaymentMethod.CREDIT_CARD, result.paymentMethod());
    }

    @Test
    @DisplayName("parseRequest - invalid AI values are removed (travelers, date outside the trip)")
    void parseRequest_ValidatesAiAnswer() {
        loginAsTraveler();
        when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
        when(llmClient.isConfigured()).thenReturn(true);
        when(llmClient.complete(anyString(), anyString(), anyInt())).thenReturn(
                "{\"type\": \"FLIGHT\", \"travelers\": 50, \"reservationDate\": \"2000-01-01\", "
                        + "\"paymentMethod\": \"BITCOIN\", \"price\": -5, \"notes\": \"window\", \"confidence\": 3}");

        ReservationAiParseResponse result = service.parseRequest(new ReservationAiParseRequest(10L, "a flight"));

        assertTrue(result.aiAvailable());
        assertEquals(ReservationType.FLIGHT, result.type());
        assertNull(result.travelers());
        assertNull(result.reservationDate());
        assertNull(result.paymentMethod());
        assertNull(result.price());
        assertEquals(1.0, result.confidence());
    }

    // ───────────────────────── admin insights ─────────────────────────

    @Test
    @DisplayName("adminInsights - without API key, alerts are computed from the real numbers")
    void adminInsights_NoApiKey_BuildsAlerts() {
        Reservation full1 = booking(1L, ReservationType.TRIP, start, ReservationStatus.CONFIRMED);
        Reservation full2 = booking(2L, ReservationType.TRIP, start, ReservationStatus.PENDING);
        when(reservationRepository.findAllWithTrip()).thenReturn(List.of(full1, full2));
        when(tripRepository.findAll()).thenReturn(List.of(trip));
        when(llmClient.isConfigured()).thenReturn(false);

        ReservationAiInsightsResponse result = service.adminInsights();

        assertFalse(result.aiAvailable());
        assertEquals("HIGH", result.alerts().get(0).severity(), "alerts are sorted by severity");
        assertTrue(result.alerts().stream().anyMatch(a -> a.title().startsWith("Fully booked")));
        assertTrue(result.alerts().stream().anyMatch(a -> a.title().contains("waiting for confirmation")));
        assertFalse(result.recommendations().isEmpty());
        assertTrue(result.summary().contains("2 reservations"));
    }
    // ───────────────────────── booking chat ─────────────────────────

    private void tripWithBookedTravelers(long booked) {
        when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
        when(reservationRepository.sumTravelersForTrip(10L, ReservationType.TRIP, ReservationStatus.CANCELLED, -1L))
                .thenReturn(booked);
    }

    private static ReservationAiChatRequest chatRequest(BookingState booking, String... texts) {
        List<Message> messages = new java.util.ArrayList<>();
        for (int i = 0; i < texts.length; i++) {
            messages.add(new Message(i % 2 == 0 ? "user" : "assistant", texts[i]));
        }
        return new ReservationAiChatRequest(10L, messages, booking);
    }

    @Test
    @DisplayName("chat - without API key, fills the booking step by step and asks for what is missing")
    void chat_NoApiKey_FillsStepByStep() {
        loginAsTraveler();
        tripWithBookedTravelers(0L);
        when(llmClient.isConfigured()).thenReturn(false);

        ReservationAiChatResponse first = service.chat(chatRequest(null, "A hotel on day 2 for 2 people around $150"));

        assertFalse(first.aiAvailable());
        assertEquals(ReservationType.HOTEL, first.booking().type());
        assertEquals(2, first.booking().travelers());
        assertEquals(start.plusDays(1), first.booking().reservationDate());
        assertEquals(150.0, first.booking().price());
        assertEquals(List.of("paymentMethod"), first.missing());
        assertFalse(first.ready());
        assertTrue(first.suggestions().contains("Cash"));
        assertTrue(first.reply().contains("pay"));

        ReservationAiChatResponse second = service.chat(chatRequest(first.booking(),
                "A hotel on day 2 for 2 people around $150", first.reply(), "cash"));

        assertTrue(second.ready());
        assertTrue(second.missing().isEmpty());
        assertTrue(second.suggestions().isEmpty());
        assertEquals(PaymentMethod.CASH, second.booking().paymentMethod());
        assertEquals(ReservationType.HOTEL, second.booking().type(), "earlier fields are kept");
        assertTrue(second.reply().contains("Review my booking"));
        verify(llmClient, never()).complete(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("chat - a bare number answers the travelers question")
    void chat_NoApiKey_BareNumberMeansTravelers() {
        loginAsTraveler();
        tripWithBookedTravelers(0L);
        when(llmClient.isConfigured()).thenReturn(false);

        ReservationAiChatResponse result = service.chat(chatRequest(
                new BookingState(ReservationType.ACTIVITY, null, start, null, null, null), "3"));

        assertEquals(3, result.booking().travelers());
        assertEquals(List.of("paymentMethod"), result.missing());
    }

    @Test
    @DisplayName("chat - the AI answer is validated and merged with the fields already collected")
    void chat_AiAnswer_IsValidatedAndMerged() {
        loginAsTraveler();
        tripWithBookedTravelers(0L);
        when(llmClient.isConfigured()).thenReturn(true);
        when(llmClient.complete(anyString(), anyString(), anyInt())).thenReturn(
                "{\"reply\": \"Dinner for two, noted! Which evening?\", \"booking\": {\"type\": \"RESTAURANT\", "
                        + "\"travelers\": null, \"reservationDate\": \"2000-01-01\", \"paymentMethod\": \"BITCOIN\", "
                        + "\"price\": 60, \"notes\": null}}");

        ReservationAiChatResponse result = service.chat(chatRequest(
                new BookingState(null, 2, null, PaymentMethod.CASH, null, null), "dinner around $60"));

        assertTrue(result.aiAvailable());
        assertEquals("Dinner for two, noted! Which evening?", result.reply());
        assertEquals(ReservationType.RESTAURANT, result.booking().type());
        assertEquals(2, result.booking().travelers(), "kept from the conversation");
        assertNull(result.booking().reservationDate(), "a date outside the trip is removed");
        assertEquals(PaymentMethod.CASH, result.booking().paymentMethod(), "an invalid payment keeps the old one");
        assertEquals(60.0, result.booking().price());
        assertNull(result.booking().notes());
        assertEquals(List.of("reservationDate"), result.missing());
        assertFalse(result.ready());
    }

    @Test
    @DisplayName("chat - a trip package cannot take more travelers than the spots left")
    void chat_TripPackage_RespectsSpotsLeft() {
        loginAsTraveler();
        tripWithBookedTravelers(3L);   // 1 spot left out of 4
        when(llmClient.isConfigured()).thenReturn(false);

        ReservationAiChatResponse result = service.chat(chatRequest(null, "The whole trip for 4 people, paying cash"));

        assertEquals(ReservationType.TRIP, result.booking().type());
        assertEquals(1, result.spotsLeft());
        assertNotNull(result.warning());
        assertTrue(result.warning().contains("Only 1 spot"));
        assertFalse(result.ready());
        assertEquals(List.of("Just me"), result.suggestions());
    }

    @Test
    @DisplayName("chat - when the AI accepts too many travelers, the reply explains the spots left")
    void chat_AiIgnoresSpotsLeft_ReplyIsCorrected() {
        loginAsTraveler();
        tripWithBookedTravelers(2L);   // 2 spots left
        when(llmClient.isConfigured()).thenReturn(true);
        when(llmClient.complete(anyString(), anyString(), anyInt())).thenReturn(
                "{\"reply\": \"Sure, the trip for 4. How would you like to pay?\", "
                        + "\"booking\": {\"type\": \"TRIP\", \"travelers\": 4}}");

        ReservationAiChatResponse result = service.chat(chatRequest(null, "the whole trip for 4 people"));

        assertFalse(result.ready());
        assertTrue(result.warning().contains("Only 2 spots"));
        assertTrue(result.reply().contains("How many travelers"), result.reply());
        assertEquals(List.of("Just me", "2 people"), result.suggestions());
    }

    @Test
    @DisplayName("chat - fields the model missed are completed with the keywords")
    void chat_AiMissesObviousFields_KeywordsFillTheGaps() {
        loginAsTraveler();
        tripWithBookedTravelers(0L);
        when(llmClient.isConfigured()).thenReturn(true);
        when(llmClient.complete(anyString(), anyString(), anyInt())).thenReturn(
                "{\"reply\": \"What type of reservation would you like?\", \"booking\": {\"type\": null}}");

        ReservationAiChatResponse result = service.chat(chatRequest(null, "dinner on day 2"));

        assertTrue(result.aiAvailable());
        assertEquals(ReservationType.RESTAURANT, result.booking().type());
        assertEquals(start.plusDays(1), result.booking().reservationDate());
        assertFalse(result.reply().contains("What type"), "the reply no longer asks for the type");
        assertEquals("travelers", result.missing().get(0));
    }

    @Test
    @DisplayName("chat - when the AI fails, the keywords answer is returned")
    void chat_AiFailure_FallsBackToKeywords() {
        loginAsTraveler();
        tripWithBookedTravelers(0L);
        when(llmClient.isConfigured()).thenReturn(true);
        when(llmClient.complete(anyString(), anyString(), anyInt()))
                .thenThrow(new ReservationLlmClient.LlmException("timeout"));

        ReservationAiChatResponse result = service.chat(chatRequest(null, "a train for 2"));

        assertFalse(result.aiAvailable());
        assertEquals(ReservationType.TRAIN, result.booking().type());
        assertEquals(2, result.booking().travelers());
        assertEquals("reservationDate", result.missing().get(0));
        assertFalse(result.suggestions().isEmpty());
    }

    @Test
    @DisplayName("adminInsights - advice about pending bookings is dropped when nothing is pending")
    void adminInsights_DropsPendingAdviceWhenNothingIsPending() {
        Reservation confirmed = booking(1L, ReservationType.TRIP, start, ReservationStatus.CONFIRMED);
        when(reservationRepository.findAllWithTrip()).thenReturn(List.of(confirmed));
        when(tripRepository.findAll()).thenReturn(List.of(trip));
        when(llmClient.isConfigured()).thenReturn(true);
        when(llmClient.complete(anyString(), anyString(), anyInt())).thenReturn(
                "{\"summary\": \"One confirmed booking.\", \"highlights\": [\"1 reservation\"], "
                        + "\"recommendations\": [\"Confirm the pending booking for Sahara Escape\", \"Promote the remaining spots\"]}");

        ReservationAiInsightsResponse result = service.adminInsights();

        assertTrue(result.aiAvailable());
        assertEquals(List.of("Promote the remaining spots"), result.recommendations());
    }
}
