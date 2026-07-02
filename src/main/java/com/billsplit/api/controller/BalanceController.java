package com.billsplit.api.controller;

import com.billsplit.api.dto.BalanceResponse;
import com.billsplit.api.dto.SettlementDto;
import com.billsplit.api.service.BalanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class BalanceController {

    private final BalanceService balanceService;

    /** Net balance per user in the group: positive = is owed, negative = owes. */
    @GetMapping("/api/groups/{groupId}/balances")
    public ResponseEntity<List<BalanceResponse>> getGroupBalances(@PathVariable Long groupId) {
        return ResponseEntity.ok(balanceService.getGroupBalances(groupId));
    }

    /** Minimal set of payments (who pays whom, how much) to settle the group. */
    @GetMapping("/api/groups/{groupId}/settlements")
    public ResponseEntity<List<SettlementDto>> getSettlementPlan(@PathVariable Long groupId) {
        return ResponseEntity.ok(balanceService.getSettlementPlan(groupId));
    }
}
