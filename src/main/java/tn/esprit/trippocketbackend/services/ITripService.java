package tn.esprit.trippocketbackend.services;

import org.springframework.web.multipart.MultipartFile;
import tn.esprit.trippocketbackend.entities.Trip;

import java.util.List;

public interface ITripService {

    Trip createTrip(Long userId, Trip trip, MultipartFile coverImage);

    List<Trip> getAllTrips();

    List<Trip> getTripsByUserId(Long userId);

    Trip getTripById(Long id);

    Trip updateTrip(Long id, Trip trip, MultipartFile coverImage);

    void deleteTrip(Long id);

    byte[] getCoverImage(Long id);
}
