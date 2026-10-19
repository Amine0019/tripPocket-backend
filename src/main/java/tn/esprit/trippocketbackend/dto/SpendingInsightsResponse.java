package tn.esprit.trippocketbackend.dto;

import java.math.BigDecimal;
import java.util.Map;

public record SpendingInsightsResponse(
        BigDecimal totalExpenses,
        Map<String, BigDecimal> spendingByCategory,
        BigDecimal totalBudget,
        BigDecimal budgetRemaining,
        BigDecimal percentageUsed,
        boolean overBudget,
        String currency,
        String aiAnalysis,
        boolean aiAvailable
) {}
