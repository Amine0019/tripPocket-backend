package tn.esprit.trippocketbackend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.trippocketbackend.entities.Activity;
import tn.esprit.trippocketbackend.entities.ActivityCategory;

import java.util.List;

public interface ActivityRepository extends JpaRepository<Activity, Long> {

    List<Activity> findByTripId(Long tripId);

    List<Activity> findByTripIdAndCategory(Long tripId, ActivityCategory category);

    List<Activity> findByTripIdAndCompleted(Long tripId, Boolean completed);
}
