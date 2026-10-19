package tn.esprit.trippocketbackend.services;

import tn.esprit.trippocketbackend.dto.SpendingInsightsResponse;
import tn.esprit.trippocketbackend.entities.BudgetExpense;

import java.util.List;

public interface IBudgetExpenseService {

    BudgetExpense createExpense(Long tripId, BudgetExpense expense);

    List<BudgetExpense> getAllExpenses();

    List<BudgetExpense> getExpensesByTripId(Long tripId);

    BudgetExpense getExpenseById(Long id);

    BudgetExpense updateExpense(Long id, BudgetExpense expense);

    void deleteExpense(Long id);

    Double getTotalByTripId(Long tripId);

    /**
     * Computes deterministic spending statistics for a trip and enriches them
     * with an AI-generated analysis from the local Ollama instance.
     *
     * @param tripId the trip whose expenses to analyse
     * @return a fully populated {@link SpendingInsightsResponse}
     */
    SpendingInsightsResponse getSpendingInsights(Long tripId);
}
