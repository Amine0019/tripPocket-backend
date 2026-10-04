package tn.esprit.trippocketbackend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.format.annotation.DateTimeFormat;
import tn.esprit.trippocketbackend.entities.Trip;
import tn.esprit.trippocketbackend.entities.TripStatus;
import tn.esprit.trippocketbackend.services.ITripService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
@Tag(name = "Trips")
public class TripController {

    private final ITripService tripService;

        @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Create a trip for a user")
        public ResponseEntity<Trip> createTrip(
            @RequestParam Long userId,
            @RequestParam String destination,
            @RequestParam(required = false) String description,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) TripStatus status,
            @RequestParam(value = "coverImage", required = false) MultipartFile coverImage) {
        Trip trip = Trip.builder()
            .destination(destination)
            .description(description)
            .startDate(startDate)
            .endDate(endDate)
            .status(status)
            .build();

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(tripService.createTrip(userId, trip, coverImage));
    }

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

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Update a trip")
    public ResponseEntity<Trip> updateTrip(
            @PathVariable Long id,
            @RequestParam(required = false) String destination,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) TripStatus status,
            @RequestParam(value = "coverImage", required = false) MultipartFile coverImage) {
        Trip trip = Trip.builder()
                .destination(destination)
                .description(description)
                .startDate(startDate)
                .endDate(endDate)
                .status(status)
                .build();

        return ResponseEntity.ok(tripService.updateTrip(id, trip, coverImage));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a trip")
    public ResponseEntity<Void> deleteTrip(@PathVariable Long id) {
        tripService.deleteTrip(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/cover-image")
    @Operation(summary = "Get a trip cover image")
    public ResponseEntity<byte[]> getCoverImage(@PathVariable Long id) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(tripService.getCoverImage(id));
    }
}
