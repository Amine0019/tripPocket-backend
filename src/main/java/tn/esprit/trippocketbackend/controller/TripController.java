package tn.esprit.trippocketbackend.controller;

//import com.fasterxml.jackson.core.type.TypeReference;
//import com.fasterxml.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.trippocketbackend.entities.ItineraryDay;
import tn.esprit.trippocketbackend.entities.Trip;
import tn.esprit.trippocketbackend.entities.TripStatus;
import tn.esprit.trippocketbackend.services.ITripService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
@Tag(name = "Trips")
public class TripController {

    private final ITripService tripService;
    private final ObjectMapper objectMapper;

    // ───────────────────────── CREATE (ADMIN) ─────────────────────────
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a trip (admin only)")
    public ResponseEntity<Trip> createTrip(
            @RequestParam(required = false) Long userId,
            @RequestParam String destination,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String subtitle,
            @RequestParam(required = false) String description,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) TripStatus status,
            @RequestParam(required = false) Integer durationDays,
            @RequestParam(required = false) Double rating,
            @RequestParam(required = false) Integer reviewCount,
            @RequestParam(required = false) String badge,
            @RequestParam(required = false) BigDecimal price,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) String availability,
            @RequestParam(required = false) Integer groupSize,
            @RequestParam(required = false) String pace,
            @RequestParam(required = false) String highlights,
            @RequestParam(required = false) String itinerary,
            @RequestParam(value = "coverImage", required = false) MultipartFile coverImage) {

        Trip trip = Trip.builder()
                .destination(destination)
                .title(title)
                .subtitle(subtitle)
                .description(description)
                .startDate(startDate)
                .endDate(endDate)
                .status(status)
                .durationDays(durationDays)
                .rating(rating)
                .reviewCount(reviewCount)
                .badge(badge)
                .price(price)
                .currency(currency)
                .availability(availability)
                .groupSize(groupSize)
                .pace(pace)
                .highlights(parseHighlights(highlights))
                .itinerary(parseItinerary(itinerary))
                .build();

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tripService.createTrip(userId, trip, coverImage));
    }

    // ───────────────────────── READ (any authenticated user) ─────────────────────────
    @GetMapping
    @Operation(summary = "Get all trips")
    public ResponseEntity<List<Trip>> getAllTrips() {
        return ResponseEntity.ok(tripService.getAllTrips());
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get trips by user ID")
    public ResponseEntity<List<Trip>> getTripsByUserId(@PathVariable Long userId) {
        return ResponseEntity.ok(tripService.getTripsByUserId(userId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a trip by ID")
    public ResponseEntity<Trip> getTripById(@PathVariable Long id) {
        return ResponseEntity.ok(tripService.getTripById(id));
    }

    @GetMapping("/{id}/cover-image")
    @Operation(summary = "Get a trip cover image")
    public ResponseEntity<byte[]> getCoverImage(@PathVariable Long id) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(tripService.getCoverImage(id));
    }

    // ───────────────────────── UPDATE (ADMIN) ─────────────────────────
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a trip (admin only)")
    public ResponseEntity<Trip> updateTrip(
            @PathVariable Long id,
            @RequestParam(required = false) String destination,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String subtitle,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) TripStatus status,
            @RequestParam(required = false) Integer durationDays,
            @RequestParam(required = false) Double rating,
            @RequestParam(required = false) Integer reviewCount,
            @RequestParam(required = false) String badge,
            @RequestParam(required = false) BigDecimal price,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) String availability,
            @RequestParam(required = false) Integer groupSize,
            @RequestParam(required = false) String pace,
            @RequestParam(required = false) String highlights,
            @RequestParam(required = false) String itinerary,
            @RequestParam(value = "coverImage", required = false) MultipartFile coverImage) {

        Trip trip = Trip.builder()
                .destination(destination)
                .title(title)
                .subtitle(subtitle)
                .description(description)
                .startDate(startDate)
                .endDate(endDate)
                .status(status)
                .durationDays(durationDays)
                .rating(rating)
                .reviewCount(reviewCount)
                .badge(badge)
                .price(price)
                .currency(currency)
                .availability(availability)
                .groupSize(groupSize)
                .pace(pace)
                .highlights(parseHighlights(highlights))
                .itinerary(parseItinerary(itinerary))
                .build();

        return ResponseEntity.ok(tripService.updateTrip(id, trip, coverImage));
    }

    // ───────────────────────── DELETE (ADMIN) ─────────────────────────
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a trip (admin only)")
    public ResponseEntity<Void> deleteTrip(@PathVariable Long id) {
        tripService.deleteTrip(id);
        return ResponseEntity.noContent().build();
    }

    // ───────────────────────── JSON helpers ─────────────────────────
    // Returns null when the param is absent (= "don't change it" on update).
    private List<String> parseHighlights(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid highlights JSON");
        }
    }

    private List<ItineraryDay> parseItinerary(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ItineraryDay>>() {});
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid itinerary JSON");
        }
    }
}