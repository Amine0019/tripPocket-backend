package tn.esprit.trippocketbackend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.trippocketbackend.entities.BudgetExpense;
import tn.esprit.trippocketbackend.entities.ExpenseCategory;

import java.util.List;

public interface BudgetExpenseRepository extends JpaRepository<BudgetExpense, Long> {

    List<BudgetExpense> findByTripId(Long tripId);

    List<BudgetExpense> findByTripIdAndCategory(Long tripId, ExpenseCategory category);
}
