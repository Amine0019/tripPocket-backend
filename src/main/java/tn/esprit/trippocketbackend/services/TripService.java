package tn.esprit.trippocketbackend.services;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.trippocketbackend.entities.Trip;
import tn.esprit.trippocketbackend.entities.TripStatus;
import tn.esprit.trippocketbackend.entities.User;
import tn.esprit.trippocketbackend.repositories.TripRepository;
import tn.esprit.trippocketbackend.repositories.UserRepository;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TripService implements ITripService {

    private final TripRepository tripRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public Trip createTrip(Long userId, Trip trip, MultipartFile coverImage) {
        validateDates(trip.getStartDate(), trip.getEndDate());

        // userId is optional: default to the connected user (the admin creating the trip)
        trip.setUser(resolveOwner(userId));

        if (trip.getStatus() == null) {
            trip.setStatus(TripStatus.PLANNED);
        }
        if (trip.getHighlights() == null) {
            trip.setHighlights(new ArrayList<>());
        }
        if (trip.getItinerary() == null) {
            trip.setItinerary(new ArrayList<>());
        }
        if (trip.getDurationDays() == null && trip.getStartDate() != null && trip.getEndDate() != null) {
            trip.setDurationDays(computeDuration(trip.getStartDate(), trip.getEndDate()));
        }

        applyCoverImage(trip, coverImage);
        return tripRepository.save(trip);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Trip> getAllTrips() {
        return tripRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Trip> getTripsByUserId(Long userId) {
        return tripRepository.findByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Trip getTripById(Long id) {
        return tripRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
    }

    @Override
    @Transactional
    public Trip updateTrip(Long id, Trip trip, MultipartFile coverImage) {
        Trip existing = getTripById(id);

        LocalDate startDate = trip.getStartDate() != null ? trip.getStartDate() : existing.getStartDate();
        LocalDate endDate = trip.getEndDate() != null ? trip.getEndDate() : existing.getEndDate();
        validateDates(startDate, endDate);

        if (trip.getDestination() != null) existing.setDestination(trip.getDestination());
        if (trip.getTitle() != null) existing.setTitle(trip.getTitle());
        if (trip.getSubtitle() != null) existing.setSubtitle(trip.getSubtitle());
        if (trip.getDescription() != null) existing.setDescription(trip.getDescription());
        if (trip.getStartDate() != null) existing.setStartDate(trip.getStartDate());
        if (trip.getEndDate() != null) existing.setEndDate(trip.getEndDate());
        if (trip.getStatus() != null) existing.setStatus(trip.getStatus());
        if (trip.getDurationDays() != null) existing.setDurationDays(trip.getDurationDays());
        if (trip.getRating() != null) existing.setRating(trip.getRating());
        if (trip.getReviewCount() != null) existing.setReviewCount(trip.getReviewCount());
        if (trip.getBadge() != null) existing.setBadge(trip.getBadge());
        if (trip.getPrice() != null) existing.setPrice(trip.getPrice());
        if (trip.getCurrency() != null) existing.setCurrency(trip.getCurrency());
        if (trip.getAvailability() != null) existing.setAvailability(trip.getAvailability());
        if (trip.getGroupSize() != null) existing.setGroupSize(trip.getGroupSize());
        if (trip.getPace() != null) existing.setPace(trip.getPace());

        // Collections: when provided (even as []), they fully replace the old ones
        if (trip.getHighlights() != null) {
            if (existing.getHighlights() == null) {
                existing.setHighlights(new ArrayList<>());
            }
            existing.getHighlights().clear();
            existing.getHighlights().addAll(trip.getHighlights());
        }
        if (trip.getItinerary() != null) {
            if (existing.getItinerary() == null) {
                existing.setItinerary(new ArrayList<>());
            }
            existing.getItinerary().clear();
            existing.getItinerary().addAll(trip.getItinerary());
        }

        applyCoverImage(existing, coverImage);

        return tripRepository.save(existing);
    }

    @Override
    @Transactional
    public void deleteTrip(Long id) {
        Trip trip = getTripById(id);
        tripRepository.delete(trip);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] getCoverImage(Long id) {
        byte[] coverImage = getTripById(id).getCoverImage();

        if (coverImage == null || coverImage.length == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip image not found");
        }

        return coverImage;
    }

    // ───────────────────────── helpers ─────────────────────────

    private User resolveOwner(Long userId) {
        if (userId != null) {
            return userRepository.findById(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }

        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private int computeDuration(LocalDate start, LocalDate end) {
        return (int) (java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1);
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