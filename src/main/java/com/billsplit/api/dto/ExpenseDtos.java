package com.billsplit.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class ExpenseDtos {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SplitInput {
        @NotNull
        private Long userId;

        @DecimalMin(value = "0.00")
        private BigDecimal shareAmount;
    }

    /**
     * Two ways to create an expense:
     *  1) Provide explicit "splits" (exact amounts per user) — amounts must sum to `amount`.
     *  2) Omit "splits" and set splitEqually=true — the API divides `amount` evenly
     *     among all current group members (remainder cents go to the payer).
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateExpenseRequest {
        @NotNull(message = "paidByUserId is required")
        private Long paidByUserId;

        @NotBlank(message = "description is required")
        private String description;

        @NotNull
        @DecimalMin(value = "0.01", message = "amount must be greater than 0")
        private BigDecimal amount;

        private boolean splitEqually;

        @Valid
        private List<SplitInput> splits;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SplitResponse {
        private Long userId;
        private String userName;
        private BigDecimal shareAmount;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpenseResponse {
        private Long id;
        private Long groupId;
        private Long paidByUserId;
        private String paidByUserName;
        private String description;
        private BigDecimal amount;
        private LocalDateTime createdAt;
        private List<SplitResponse> splits;
    }
}
