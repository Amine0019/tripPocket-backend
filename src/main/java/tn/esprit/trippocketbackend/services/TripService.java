package tn.esprit.trippocketbackend.services;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.trippocketbackend.entities.Trip;
import tn.esprit.trippocketbackend.entities.TripStatus;
import tn.esprit.trippocketbackend.repositories.TripRepository;
import tn.esprit.trippocketbackend.repositories.UserRepository;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TripService implements ITripService {

    private final TripRepository tripRepository;
    private final UserRepository userRepository;

    @Override
    public Trip createTrip(Long userId, Trip trip, MultipartFile coverImage) {
        validateDates(trip.getStartDate(), trip.getEndDate());

        trip.setUser(userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")));

        if (trip.getStatus() == null) {
            trip.setStatus(TripStatus.PLANNED);
        }

        applyCoverImage(trip, coverImage);
        return tripRepository.save(trip);
    }

    @Override
    public List<Trip> getAllTrips() {
        return tripRepository.findAll();
    }

    @Override
    public List<Trip> getTripsByUserId(Long userId) {
        return tripRepository.findByUserId(userId);
    }

    @Override
    public Trip getTripById(Long id) {
        return tripRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
    }

    @Override
    public Trip updateTrip(Long id, Trip trip, MultipartFile coverImage) {
        Trip existingTrip = getTripById(id);

        LocalDate startDate = trip.getStartDate() != null ? trip.getStartDate() : existingTrip.getStartDate();
        LocalDate endDate = trip.getEndDate() != null ? trip.getEndDate() : existingTrip.getEndDate();
        validateDates(startDate, endDate);

        if (trip.getDestination() != null) {
            existingTrip.setDestination(trip.getDestination());
        }
        if (trip.getDescription() != null) {
            existingTrip.setDescription(trip.getDescription());
        }
        if (trip.getStartDate() != null) {
            existingTrip.setStartDate(trip.getStartDate());
        }
        if (trip.getEndDate() != null) {
            existingTrip.setEndDate(trip.getEndDate());
        }
        if (trip.getStatus() != null) {
            existingTrip.setStatus(trip.getStatus());
        }

        applyCoverImage(existingTrip, coverImage);

        return tripRepository.save(existingTrip);
    }

    @Override
    public void deleteTrip(Long id) {
        Trip trip = getTripById(id);
        tripRepository.delete(trip);
    }

    @Override
    public byte[] getCoverImage(Long id) {
        byte[] coverImage = getTripById(id).getCoverImage();

        if (coverImage == null || coverImage.length == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip image not found");
        }

        return coverImage;
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End date cannot be before start date");
        }
    }

    private void applyCoverImage(Trip trip, MultipartFile coverImage) {
        if (coverImage == null || coverImage.isEmpty()) {
            return;
        }

        String contentType = coverImage.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File must be an image");
        }

        try {
            trip.setCoverImage(coverImage.getBytes());
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to read image file",
                    exception
            );
        }
    }
}
