package tn.esprit.trippocketbackend.services;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.trippocketbackend.entities.Activity;
import tn.esprit.trippocketbackend.repositories.ActivityRepository;
import tn.esprit.trippocketbackend.repositories.TripRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ActivityService implements IActivityService {

    private final ActivityRepository activityRepository;
    private final TripRepository tripRepository;

    @Override
    public Activity createActivity(Long tripId, Activity activity) {
        validateActivity(activity);

        activity.setTrip(tripRepository.findById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found")));

        if (activity.getCompleted() == null) {
            activity.setCompleted(false);
        }

        return activityRepository.save(activity);
    }

    @Override
    public List<Activity> getAllActivities() {
        return activityRepository.findAll();
    }

    @Override
    public List<Activity> getActivitiesByTripId(Long tripId) {
        return activityRepository.findByTripId(tripId);
    }

    @Override
    public Activity getActivityById(Long id) {
        return activityRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Activity not found"));
    }

    @Override
    public Activity updateActivity(Long id, Activity activity) {
        validateActivity(activity);

        Activity existingActivity = getActivityById(id);
        existingActivity.setName(activity.getName());
        existingActivity.setDescription(activity.getDescription());
        existingActivity.setActivityDate(activity.getActivityDate());
        existingActivity.setLocation(activity.getLocation());
        existingActivity.setEstimatedCost(activity.getEstimatedCost());
        existingActivity.setCompleted(activity.getCompleted() == null ? false : activity.getCompleted());
        existingActivity.setCategory(activity.getCategory());

        return activityRepository.save(existingActivity);
    }

    @Override
    public Activity updateCompleted(Long id, Boolean completed) {
        Activity activity = getActivityById(id);
        activity.setCompleted(completed);
        return activityRepository.save(activity);
    }

    @Override
    public void deleteActivity(Long id) {
        Activity activity = getActivityById(id);
        activityRepository.delete(activity);
    }

    private void validateActivity(Activity activity) {
        if (activity.getName() == null || activity.getName().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Activity name is required");
        }
        if (activity.getEstimatedCost() != null && activity.getEstimatedCost() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estimated cost cannot be negative");
        }
    }
}
