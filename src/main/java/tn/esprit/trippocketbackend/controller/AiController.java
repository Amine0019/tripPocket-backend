package tn.esprit.trippocketbackend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.trippocketbackend.dto.AiItineraryRequest;
import tn.esprit.trippocketbackend.dto.AiTaglineRequest;
import tn.esprit.trippocketbackend.dto.AiTaglineResponse;
import tn.esprit.trippocketbackend.entities.ItineraryDay;
import tn.esprit.trippocketbackend.services.AiService;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "AI assistant")
public class AiController {

    private final AiService aiService;

    @PostMapping("/trip-tagline")
    @Operation(summary = "Generate a headline and short description from 2-3 keywords (admin only)")
    public ResponseEntity<AiTaglineResponse> tagline(@RequestBody AiTaglineRequest request) {
        return ResponseEntity.ok(aiService.generateTagline(request));
    }

    @PostMapping("/trip-itinerary")
    @Operation(summary = "Suggest a day-by-day itinerary for a destination (admin only)")
    public ResponseEntity<List<ItineraryDay>> itinerary(@RequestBody AiItineraryRequest request) {
        return ResponseEntity.ok(aiService.generateItinerary(request));
    }
}