package tn.esprit.trippocketbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for the AI Spending Insights endpoint.
 * <p>
 * All monetary fields are computed deterministically using {@link BigDecimal}.
 * The AI-generated fields ({@code aiAnalysis}, {@code aiRecommendations}) are
 * populated by Ollama and may be {@code null} if the model is unavailable.
 * </p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpendingInsightsResponse {

    /** The trip identifier these insights belong to. */
    private Long tripId;

    /** Total amount spent across all expenses for the trip. */
    private BigDecimal totalExpenses;

    /**
     * Total budget configured for the trip.
     * Currently 0 because the Trip entity does not carry a budget field;
     * this field is reserved for future budget support.
     */
    private BigDecimal totalBudget;

    /** Remaining budget (totalBudget - totalExpenses); may be negative. */
    private BigDecimal budgetRemaining;

    /** Percentage of budget consumed (0–100+); 0 when totalBudget is zero. */
    private BigDecimal budgetUsedPercent;

    /** True when totalExpenses exceeds totalBudget and totalBudget > 0. */
    private boolean overBudget;

    /** Spending amount keyed by category name (e.g. "FOOD", "TRANSPORT"). */
    private Map<String, BigDecimal> categoryBreakdown;

    /** Category names sorted descending by spend, up to 3 entries. */
    private List<String> largestCategories;

    /** Total number of expense records included in the analysis. */
    private int expenseCount;

    /** Short AI-generated spending summary; {@code null} when Ollama is unavailable. */
    private String aiAnalysis;

    /** Practical AI-generated savings recommendations; {@code null} when Ollama is unavailable. */
    private String aiRecommendations;

    /** Whether the AI model was reachable and produced output. */
    private boolean aiAvailable;

    /** Human-readable error message when AI is unavailable (for frontend display). */
    private String aiError;
}
