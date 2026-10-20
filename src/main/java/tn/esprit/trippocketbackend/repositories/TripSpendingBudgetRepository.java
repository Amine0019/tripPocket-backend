package tn.esprit.trippocketbackend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.trippocketbackend.entities.TripSpendingBudget;

import java.util.Optional;

public interface TripSpendingBudgetRepository extends JpaRepository<TripSpendingBudget, Long> {
    Optional<TripSpendingBudget> findByTripId(Long tripId);
}
