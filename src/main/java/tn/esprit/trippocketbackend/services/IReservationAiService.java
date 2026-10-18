package tn.esprit.trippocketbackend.services;

import tn.esprit.trippocketbackend.dto.ReservationAiInsightsResponse;
import tn.esprit.trippocketbackend.dto.ReservationAiParseRequest;
import tn.esprit.trippocketbackend.dto.ReservationAiParseResponse;
import tn.esprit.trippocketbackend.dto.TripCheckResponse;

public interface IReservationAiService {

    /** Checks the connected user's bookings for a trip and suggests what is missing. */
    TripCheckResponse checkTrip(Long tripId);

    /** Turns one sentence into booking form values (nothing is saved). */
    ReservationAiParseResponse parseRequest(ReservationAiParseRequest request);

    /** Summary, alerts and recommended actions for the admin dashboard. */
    ReservationAiInsightsResponse adminInsights();
}
