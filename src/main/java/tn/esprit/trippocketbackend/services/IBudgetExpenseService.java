package tn.esprit.trippocketbackend.services;

import tn.esprit.trippocketbackend.entities.BudgetExpense;
import tn.esprit.trippocketbackend.dto.SpendingInsightsResponse;

import java.util.List;

public interface IBudgetExpenseService {

    BudgetExpense createExpense(Long tripId, BudgetExpense expense);

    List<BudgetExpense> getAllExpenses();

    List<BudgetExpense> getExpensesByTripId(Long tripId);

    BudgetExpense getExpenseById(Long id);

    BudgetExpense updateExpense(Long id, BudgetExpense expense);

    void deleteExpense(Long id);

    Double getTotalByTripId(Long tripId);

    SpendingInsightsResponse getSpendingInsights(Long tripId);

    void saveTripBudget(Long tripId, java.math.BigDecimal amount);
}
