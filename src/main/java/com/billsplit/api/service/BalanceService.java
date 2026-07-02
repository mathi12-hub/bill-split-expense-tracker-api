package com.billsplit.api.service;

import com.billsplit.api.dto.BalanceResponse;
import com.billsplit.api.dto.SettlementDto;
import com.billsplit.api.dto.UserBalanceProjection;
import com.billsplit.api.repository.ExpenseSplitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

@Service
@RequiredArgsConstructor
public class BalanceService {

    private final ExpenseSplitRepository expenseSplitRepository;
    private final GroupService groupService;

    private static final BigDecimal EPSILON = new BigDecimal("0.01");

    @Transactional(readOnly = true)
    public List<BalanceResponse> getGroupBalances(Long groupId) {
        groupService.findGroupOrThrow(groupId); // 404 if group doesn't exist

        List<UserBalanceProjection> rows = expenseSplitRepository.calculateGroupBalances(groupId);
        return rows.stream()
                .map(r -> BalanceResponse.builder()
                        .userId(r.getUserId())
                        .userName(r.getUserName())
                        .totalPaid(r.getTotalPaid())
                        .totalOwed(r.getTotalOwed())
                        .netBalance(r.getNetBalance())
                        .build())
                .toList();
    }

    /**
     * Greedy debt-simplification algorithm: repeatedly matches the user who is
     * owed the most against the user who owes the most, settling the smaller
     * of the two amounts each time. Minimizes the number of transactions
     * needed to settle the whole group (a classic "cash flow minimization" approach).
     */
    @Transactional(readOnly = true)
    public List<SettlementDto> getSettlementPlan(Long groupId) {
        List<BalanceResponse> balances = getGroupBalances(groupId);

        PriorityQueue<BalanceResponse> creditors = new PriorityQueue<>(
                Comparator.comparing(BalanceResponse::getNetBalance).reversed());
        PriorityQueue<BalanceResponse> debtors = new PriorityQueue<>(
                Comparator.comparing(BalanceResponse::getNetBalance));

        for (BalanceResponse b : balances) {
            if (b.getNetBalance().compareTo(EPSILON) > 0) {
                creditors.add(b);
            } else if (b.getNetBalance().compareTo(EPSILON.negate()) < 0) {
                debtors.add(b);
            }
        }

        List<SettlementDto> settlements = new ArrayList<>();

        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            BalanceResponse creditor = creditors.poll();
            BalanceResponse debtor = debtors.poll();

            BigDecimal owed = debtor.getNetBalance().abs();
            BigDecimal receivable = creditor.getNetBalance();
            BigDecimal settleAmount = owed.min(receivable);

            settlements.add(SettlementDto.builder()
                    .fromUserId(debtor.getUserId())
                    .fromUserName(debtor.getUserName())
                    .toUserId(creditor.getUserId())
                    .toUserName(creditor.getUserName())
                    .amount(settleAmount)
                    .build());

            BigDecimal remainingCredit = receivable.subtract(settleAmount);
            BigDecimal remainingDebt = owed.subtract(settleAmount);

            if (remainingCredit.compareTo(EPSILON) > 0) {
                creditors.add(BalanceResponse.builder()
                        .userId(creditor.getUserId())
                        .userName(creditor.getUserName())
                        .totalPaid(creditor.getTotalPaid())
                        .totalOwed(creditor.getTotalOwed())
                        .netBalance(remainingCredit)
                        .build());
            }
            if (remainingDebt.compareTo(EPSILON) > 0) {
                debtors.add(BalanceResponse.builder()
                        .userId(debtor.getUserId())
                        .userName(debtor.getUserName())
                        .totalPaid(debtor.getTotalPaid())
                        .totalOwed(debtor.getTotalOwed())
                        .netBalance(remainingDebt.negate())
                        .build());
            }
        }

        return settlements;
    }
}
