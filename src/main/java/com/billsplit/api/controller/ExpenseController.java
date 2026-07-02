package com.billsplit.api.controller;

import com.billsplit.api.dto.ExpenseDtos.CreateExpenseRequest;
import com.billsplit.api.dto.ExpenseDtos.ExpenseResponse;
import com.billsplit.api.service.ExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    @PostMapping("/api/groups/{groupId}/expenses")
    public ResponseEntity<ExpenseResponse> createExpense(
            @PathVariable Long groupId,
            @Valid @RequestBody CreateExpenseRequest request) {
        return new ResponseEntity<>(expenseService.createExpense(groupId, request), HttpStatus.CREATED);
    }

    @GetMapping("/api/groups/{groupId}/expenses")
    public ResponseEntity<List<ExpenseResponse>> getExpensesForGroup(@PathVariable Long groupId) {
        return ResponseEntity.ok(expenseService.getExpensesForGroup(groupId));
    }

    @GetMapping("/api/expenses/{id}")
    public ResponseEntity<ExpenseResponse> getExpense(@PathVariable Long id) {
        return ResponseEntity.ok(expenseService.getExpense(id));
    }

    @DeleteMapping("/api/expenses/{id}")
    public ResponseEntity<Void> deleteExpense(@PathVariable Long id) {
        expenseService.deleteExpense(id);
        return ResponseEntity.noContent().build();
    }
}
