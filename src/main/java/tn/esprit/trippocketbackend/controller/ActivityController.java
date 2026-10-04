package tn.esprit.trippocketbackend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.trippocketbackend.entities.Activity;
import tn.esprit.trippocketbackend.services.IActivityService;

import java.util.List;

@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
@Tag(name = "Activities")
public class ActivityController {

    private final IActivityService activityService;

    @PostMapping
    @Operation(summary = "Create an activity for a trip")
    public ResponseEntity<Activity> createActivity(
            @RequestParam Long tripId,
            @RequestBody Activity activity) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(activityService.createActivity(tripId, activity));
    }

    @GetMapping
    @Operation(summary = "Get all activities")
    public ResponseEntity<List<Activity>> getAllActivities() {
        return ResponseEntity.ok(activityService.getAllActivities());
    }

    @GetMapping("/trip/{tripId}")
    @Operation(summary = "Get activities by trip ID")
    public ResponseEntity<List<Activity>> getActivitiesByTripId(@PathVariable Long tripId) {
        return ResponseEntity.ok(activityService.getActivitiesByTripId(tripId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an activity by ID")
    public ResponseEntity<Activity> getActivityById(@PathVariable Long id) {
        return ResponseEntity.ok(activityService.getActivityById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an activity")
    public ResponseEntity<Activity> updateActivity(
            @PathVariable Long id,
            @RequestBody Activity activity) {
        return ResponseEntity.ok(activityService.updateActivity(id, activity));
    }

    @PatchMapping("/{id}/completed")
    @Operation(summary = "Update activity completion status")
    public ResponseEntity<Activity> updateCompleted(
            @PathVariable Long id,
            @RequestParam Boolean completed) {
        return ResponseEntity.ok(activityService.updateCompleted(id, completed));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an activity")
    public ResponseEntity<Void> deleteActivity(@PathVariable Long id) {
        activityService.deleteActivity(id);
        return ResponseEntity.noContent().build();
    }
}
