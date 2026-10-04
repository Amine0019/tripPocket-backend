package tn.esprit.trippocketbackend.services;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.trippocketbackend.entities.BudgetExpense;
import tn.esprit.trippocketbackend.repositories.BudgetExpenseRepository;
import tn.esprit.trippocketbackend.repositories.TripRepository;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BudgetExpenseService implements IBudgetExpenseService {

    private final BudgetExpenseRepository budgetExpenseRepository;
    private final TripRepository tripRepository;

    @Override
    public BudgetExpense createExpense(Long tripId, BudgetExpense expense) {
        validateAmount(expense.getAmount());

        expense.setTrip(tripRepository.findById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found")));

        if (expense.getExpenseDate() == null) {
            expense.setExpenseDate(LocalDate.now());
        }

        return budgetExpenseRepository.save(expense);
    }

    @Override
    public List<BudgetExpense> getAllExpenses() {
        return budgetExpenseRepository.findAll();
    }

    @Override
    public List<BudgetExpense> getExpensesByTripId(Long tripId) {
        return budgetExpenseRepository.findByTripId(tripId);
    }

    @Override
    public BudgetExpense getExpenseById(Long id) {
        return budgetExpenseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found"));
    }

    @Override
    public BudgetExpense updateExpense(Long id, BudgetExpense expense) {
        validateAmount(expense.getAmount());

        BudgetExpense existingExpense = getExpenseById(id);
        existingExpense.setAmount(expense.getAmount());
        existingExpense.setExpenseDate(expense.getExpenseDate());
        existingExpense.setDescription(expense.getDescription());
        existingExpense.setCategory(expense.getCategory());
        existingExpense.setPaymentMethod(expense.getPaymentMethod());

        return budgetExpenseRepository.save(existingExpense);
    }

    @Override
    public void deleteExpense(Long id) {
        BudgetExpense expense = getExpenseById(id);
        budgetExpenseRepository.delete(expense);
    }

    @Override
    public Double getTotalByTripId(Long tripId) {
        tripRepository.findById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));

        return budgetExpenseRepository.findByTripId(tripId).stream()
                .map(BudgetExpense::getAmount)
                .filter(amount -> amount != null)
                .mapToDouble(Double::doubleValue)
                .sum();
    }

    private void validateAmount(Double amount) {
        if (amount == null || amount <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be greater than zero");
        }
    }
}
