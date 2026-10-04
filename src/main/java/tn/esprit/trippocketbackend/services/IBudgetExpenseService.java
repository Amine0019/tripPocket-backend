package tn.esprit.trippocketbackend.services;

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
}
