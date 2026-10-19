package tn.esprit.trippocketbackend.services;

import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.trippocketbackend.entities.BudgetExpense;
import tn.esprit.trippocketbackend.repositories.BudgetExpenseRepository;
import tn.esprit.trippocketbackend.repositories.TripRepository;
import tn.esprit.trippocketbackend.repositories.TripSpendingBudgetRepository;
import tn.esprit.trippocketbackend.entities.TripSpendingBudget;
import tn.esprit.trippocketbackend.dto.SpendingInsightsResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import tn.esprit.trippocketbackend.entities.ExpenseCategory;

@Service
@RequiredArgsConstructor
public class BudgetExpenseService implements IBudgetExpenseService {

    private final BudgetExpenseRepository budgetExpenseRepository;
    private final TripRepository tripRepository;
    private final TripSpendingBudgetRepository spendingBudgetRepository;

    private static final HttpClient OLLAMA_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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

    @Override
    public void saveTripBudget(Long tripId, BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Budget must be zero or greater");
        }
        var trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
        TripSpendingBudget budget = spendingBudgetRepository.findByTripId(tripId).orElseGet(TripSpendingBudget::new);
        budget.setTrip(trip);
        budget.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
        spendingBudgetRepository.save(budget);
    }

    @Override
    public SpendingInsightsResponse getSpendingInsights(Long tripId) {
        var trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        for (ExpenseCategory category : ExpenseCategory.values()) byCategory.put(category.name(), BigDecimal.ZERO.setScale(2));
        BigDecimal total = budgetExpenseRepository.findByTripId(tripId).stream()
                .filter(expense -> expense.getAmount() != null)
                .filter(expense -> Double.isFinite(expense.getAmount()))
                .map(expense -> {
                    String category = expense.getCategory() == null ? "UNCATEGORIZED" : expense.getCategory().name();
                    byCategory.merge(category, BigDecimal.valueOf(expense.getAmount()), BigDecimal::add);
                    return BigDecimal.valueOf(expense.getAmount());
                }).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal budget = spendingBudgetRepository.findByTripId(tripId)
                .map(TripSpendingBudget::getAmount).orElse(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal remaining = budget.subtract(total).setScale(2, RoundingMode.HALF_UP);
        BigDecimal percentage = budget.signum() == 0 ? null
                : total.multiply(BigDecimal.valueOf(100)).divide(budget, 2, RoundingMode.HALF_UP);
        String currency = trip.getCurrency() == null || trip.getCurrency().isBlank() ? "USD" : trip.getCurrency();
        Map<String, BigDecimal> normalized = byCategory.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> e.getValue().setScale(2, RoundingMode.HALF_UP),
                        (left, right) -> left, LinkedHashMap::new));
        String analysis = generateAnalysis(total, normalized, budget, remaining, percentage, currency);
        return new SpendingInsightsResponse(total, normalized, budget, remaining, percentage,
                total.compareTo(budget) > 0, currency, analysis, analysis != null);
    }

    private String generateAnalysis(BigDecimal total, Map<String, BigDecimal> categories,
                                   BigDecimal budget, BigDecimal remaining, BigDecimal percentage, String currency) {
        try {
            String statistics = OBJECT_MAPPER.writeValueAsString(Map.of(
                    "currency", currency, "totalExpenses", total, "spendingByCategory", categories,
                    "totalBudget", budget, "budgetRemaining", remaining,
                    "percentageUsed", percentage == null ? "unavailable for a zero budget" : percentage));
            String prompt = "You are a concise spending coach. Use only these verified aggregate statistics: " + statistics
                    + ". Do not recalculate or state monetary values or percentages. Do not invent data. "
                    + "Return a short analysis with observations and practical savings recommendations, grounded only in the categories and whether the budget is positive or zero.";
            String payload = OBJECT_MAPPER.writeValueAsString(Map.of("model", "qwen2.5:3b", "prompt", prompt, "stream", false));
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:11434/api/generate"))
                    .timeout(Duration.ofSeconds(12)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
            HttpResponse<String> response = OLLAMA_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) return null;
            JsonNode result = OBJECT_MAPPER.readTree(response.body()).get("response");
            return result == null || result.asText().isBlank() ? null : result.asText().trim();
        } catch (Exception ignored) {
            return null;
        }
    }

    private void validateAmount(Double amount) {
        if (amount == null || !Double.isFinite(amount) || amount <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be greater than zero");
        }
    }
}
