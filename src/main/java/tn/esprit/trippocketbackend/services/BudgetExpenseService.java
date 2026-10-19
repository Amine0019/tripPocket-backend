package tn.esprit.trippocketbackend.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.trippocketbackend.dto.SpendingInsightsResponse;
import tn.esprit.trippocketbackend.entities.BudgetExpense;
import tn.esprit.trippocketbackend.entities.ExpenseCategory;
import tn.esprit.trippocketbackend.repositories.BudgetExpenseRepository;
import tn.esprit.trippocketbackend.repositories.TripRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BudgetExpenseService implements IBudgetExpenseService {

    private static final String OLLAMA_URL = "http://localhost:11434/api/generate";
    private static final String OLLAMA_MODEL = "qwen2.5:3b";
    private static final Duration OLLAMA_TIMEOUT = Duration.ofSeconds(30);

    private final BudgetExpenseRepository budgetExpenseRepository;
    private final TripRepository tripRepository;

    // ── Existing operations (unchanged) ────────────────────────────────────────

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
        existingExpense.setTitle(expense.getTitle());
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

    // ── AI Spending Insights ────────────────────────────────────────────────────

    @Override
    public SpendingInsightsResponse getSpendingInsights(Long tripId) {
        // Validate trip exists
        tripRepository.findById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));

        List<BudgetExpense> expenses = budgetExpenseRepository.findByTripId(tripId);

        // ── 1. Deterministic financial calculations (BigDecimal) ─────────────
        BigDecimal totalExpenses = expenses.stream()
                .filter(e -> e.getAmount() != null)
                .map(e -> BigDecimal.valueOf(e.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Category breakdown
        Map<String, BigDecimal> categoryBreakdown = new LinkedHashMap<>();
        for (ExpenseCategory cat : ExpenseCategory.values()) {
            categoryBreakdown.put(cat.name(), BigDecimal.ZERO);
        }
        for (BudgetExpense e : expenses) {
            if (e.getAmount() != null && e.getCategory() != null) {
                String key = e.getCategory().name();
                categoryBreakdown.merge(key, BigDecimal.valueOf(e.getAmount()), BigDecimal::add);
            }
        }

        // Largest categories (top 3, descending by amount)
        List<String> largestCategories = categoryBreakdown.entrySet().stream()
                .filter(entry -> entry.getValue().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // Budget: the Trip entity does not carry a budget field yet; reserved for future use
        BigDecimal totalBudget = BigDecimal.ZERO;
        BigDecimal budgetRemaining = totalBudget.subtract(totalExpenses);
        BigDecimal budgetUsedPercent = BigDecimal.ZERO;
        boolean overBudget = false;

        if (totalBudget.compareTo(BigDecimal.ZERO) > 0) {
            budgetUsedPercent = totalExpenses
                    .multiply(BigDecimal.valueOf(100))
                    .divide(totalBudget, 2, RoundingMode.HALF_UP);
            overBudget = totalExpenses.compareTo(totalBudget) > 0;
        }

        // ── 2. Build base response (always returned) ─────────────────────────
        SpendingInsightsResponse response = SpendingInsightsResponse.builder()
                .tripId(tripId)
                .totalExpenses(totalExpenses.setScale(2, RoundingMode.HALF_UP))
                .totalBudget(totalBudget)
                .budgetRemaining(budgetRemaining.setScale(2, RoundingMode.HALF_UP))
                .budgetUsedPercent(budgetUsedPercent)
                .overBudget(overBudget)
                .categoryBreakdown(categoryBreakdown)
                .largestCategories(largestCategories)
                .expenseCount(expenses.size())
                .aiAvailable(false)
                .build();

        if (expenses.isEmpty()) {
            response.setAiError("No expenses recorded yet — add some expenses to generate insights.");
            return response;
        }

        // ── 3. AI enrichment via Ollama (failures do not affect computed stats) ─
        try {
            String prompt = buildPrompt(totalExpenses, categoryBreakdown, largestCategories, expenses.size());
            String aiRaw = callOllama(prompt);
            parseAndSetAiFields(response, aiRaw);
        } catch (Exception ex) {
            log.warn("Ollama unavailable for trip {}: {}", tripId, ex.getMessage());
            response.setAiError("AI insights are temporarily unavailable. Spending statistics are still accurate.");
        }

        return response;
    }

    // ── Private helpers ─────────────────────────────────────────────────────────

    /**
     * Builds a minimal, structured prompt that gives the model only aggregated statistics.
     * Raw expense titles and descriptions are intentionally excluded to avoid leaking PII
     * and to keep the prompt concise.
     */
    private String buildPrompt(BigDecimal total,
                               Map<String, BigDecimal> breakdown,
                               List<String> top3,
                               int count) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a travel finance advisor. Based on the following aggregated trip spending data, ");
        sb.append("provide: (1) a concise 2-3 sentence spending analysis, and (2) 3 practical savings tips.\n\n");
        sb.append("SPENDING SUMMARY:\n");
        sb.append("- Total expenses: $").append(total.toPlainString()).append("\n");
        sb.append("- Number of expense records: ").append(count).append("\n");
        sb.append("- Spending by category:\n");
        breakdown.forEach((cat, amt) -> {
            if (amt.compareTo(BigDecimal.ZERO) > 0) {
                sb.append("  * ").append(cat).append(": $").append(amt.setScale(2, RoundingMode.HALF_UP).toPlainString()).append("\n");
            }
        });
        if (!top3.isEmpty()) {
            sb.append("- Top spending categories: ").append(String.join(", ", top3)).append("\n");
        }
        sb.append("\nRespond in this exact format:\n");
        sb.append("ANALYSIS: <your 2-3 sentence analysis here>\n");
        sb.append("RECOMMENDATIONS: <your 3 numbered tips here>\n");
        sb.append("\nDo not invent amounts, categories, or trips not listed above. Be concise.");
        return sb.toString();
    }

    /**
     * Calls the local Ollama API in non-streaming mode.
     *
     * @param prompt the user prompt
     * @return the raw text response from Ollama
     * @throws Exception on timeout, connection failure, or non-200 status
     */
    private String callOllama(String prompt) throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        String requestBody = mapper.writeValueAsString(Map.of(
                "model", OLLAMA_MODEL,
                "prompt", prompt,
                "stream", false
        ));

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(OLLAMA_TIMEOUT)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OLLAMA_URL))
                .timeout(OLLAMA_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> httpResponse = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (httpResponse.statusCode() != 200) {
            throw new RuntimeException("Ollama returned HTTP " + httpResponse.statusCode());
        }

        JsonNode root = mapper.readTree(httpResponse.body());
        JsonNode responseNode = root.get("response");
        if (responseNode == null || responseNode.isNull()) {
            throw new RuntimeException("Ollama response missing 'response' field");
        }
        return responseNode.asText();
    }

    /**
     * Parses the raw Ollama output and sets the AI fields on the response.
     * Falls back to using the entire text as analysis if the expected markers are absent.
     */
    private void parseAndSetAiFields(SpendingInsightsResponse response, String raw) {
        String analysis = null;
        String recommendations = null;

        int analysisIdx = raw.indexOf("ANALYSIS:");
        int recsIdx = raw.indexOf("RECOMMENDATIONS:");

        if (analysisIdx >= 0 && recsIdx > analysisIdx) {
            analysis = raw.substring(analysisIdx + "ANALYSIS:".length(), recsIdx).trim();
            recommendations = raw.substring(recsIdx + "RECOMMENDATIONS:".length()).trim();
        } else if (analysisIdx >= 0) {
            analysis = raw.substring(analysisIdx + "ANALYSIS:".length()).trim();
        } else {
            // Model did not follow the format — use the whole response as analysis
            analysis = raw.trim();
        }

        response.setAiAnalysis(analysis);
        response.setAiRecommendations(recommendations);
        response.setAiAvailable(true);
    }

    private void validateAmount(Double amount) {
        if (amount == null || amount <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be greater than zero");
        }
    }
}

