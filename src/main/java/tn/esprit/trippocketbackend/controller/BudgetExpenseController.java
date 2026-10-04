package tn.esprit.trippocketbackend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.trippocketbackend.entities.BudgetExpense;
import tn.esprit.trippocketbackend.services.IBudgetExpenseService;

import java.util.List;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
@Tag(name = "Budget Expenses")
public class BudgetExpenseController {

    private final IBudgetExpenseService budgetExpenseService;

    @PostMapping
    @Operation(summary = "Create a budget expense for a trip")
    public ResponseEntity<BudgetExpense> createExpense(
            @RequestParam Long tripId,
            @RequestBody BudgetExpense expense) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(budgetExpenseService.createExpense(tripId, expense));
    }

    @GetMapping
    @Operation(summary = "Get all budget expenses")
    public ResponseEntity<List<BudgetExpense>> getAllExpenses() {
        return ResponseEntity.ok(budgetExpenseService.getAllExpenses());
    }

    @GetMapping("/trip/{tripId}")
    @Operation(summary = "Get budget expenses by trip ID")
    public ResponseEntity<List<BudgetExpense>> getExpensesByTripId(@PathVariable Long tripId) {
        return ResponseEntity.ok(budgetExpenseService.getExpensesByTripId(tripId));
    }

    @GetMapping("/trip/{tripId}/total")
    @Operation(summary = "Get total expenses for a trip")
    public ResponseEntity<Double> getTotalByTripId(@PathVariable Long tripId) {
        return ResponseEntity.ok(budgetExpenseService.getTotalByTripId(tripId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a budget expense by ID")
    public ResponseEntity<BudgetExpense> getExpenseById(@PathVariable Long id) {
        return ResponseEntity.ok(budgetExpenseService.getExpenseById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a budget expense")
    public ResponseEntity<BudgetExpense> updateExpense(
            @PathVariable Long id,
            @RequestBody BudgetExpense expense) {
        return ResponseEntity.ok(budgetExpenseService.updateExpense(id, expense));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a budget expense")
    public ResponseEntity<Void> deleteExpense(@PathVariable Long id) {
        budgetExpenseService.deleteExpense(id);
        return ResponseEntity.noContent().build();
    }
}
