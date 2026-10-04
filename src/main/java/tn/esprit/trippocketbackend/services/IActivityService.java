package tn.esprit.trippocketbackend.services;

import tn.esprit.trippocketbackend.entities.Activity;

import java.util.List;

public interface IActivityService {

    Activity createActivity(Long tripId, Activity activity);

    List<Activity> getAllActivities();

    List<Activity> getActivitiesByTripId(Long tripId);

    Activity getActivityById(Long id);

    Activity updateActivity(Long id, Activity activity);

    Activity updateCompleted(Long id, Boolean completed);

    void deleteActivity(Long id);
}
