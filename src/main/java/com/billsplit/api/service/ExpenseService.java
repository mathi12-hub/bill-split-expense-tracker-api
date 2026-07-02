package com.billsplit.api.service;

import com.billsplit.api.dto.ExpenseDtos.CreateExpenseRequest;
import com.billsplit.api.dto.ExpenseDtos.ExpenseResponse;
import com.billsplit.api.dto.ExpenseDtos.SplitInput;
import com.billsplit.api.dto.ExpenseDtos.SplitResponse;
import com.billsplit.api.entity.Expense;
import com.billsplit.api.entity.ExpenseSplit;
import com.billsplit.api.entity.Group;
import com.billsplit.api.entity.GroupMember;
import com.billsplit.api.entity.User;
import com.billsplit.api.exception.BadRequestException;
import com.billsplit.api.exception.ResourceNotFoundException;
import com.billsplit.api.repository.ExpenseRepository;
import com.billsplit.api.repository.GroupMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupService groupService;
    private final UserService userService;

    @Transactional
    public ExpenseResponse createExpense(Long groupId, CreateExpenseRequest request) {
        Group group = groupService.findGroupOrThrow(groupId);
        User paidBy = userService.findUserOrThrow(request.getPaidByUserId());

        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, paidBy.getId())) {
            throw new BadRequestException("Payer (userId=" + paidBy.getId() + ") is not a member of group " + groupId);
        }

        Expense expense = Expense.builder()
                .group(group)
                .paidBy(paidBy)
                .description(request.getDescription())
                .amount(request.getAmount())
                .build();

        List<ExpenseSplit> splits = request.isSplitEqually()
                ? buildEqualSplits(expense, groupId, request.getAmount())
                : buildExactSplits(expense, groupId, request.getAmount(), request.getSplits());

        expense.getSplits().addAll(splits);
        Expense saved = expenseRepository.save(expense);
        return toResponse(saved);
    }

    /**
     * Splits the amount evenly across every current group member.
     * Any leftover cents from integer division (due to rounding) are
     * added to the payer's share so the splits always sum exactly to `amount`.
     */
    private List<ExpenseSplit> buildEqualSplits(Expense expense, Long groupId, BigDecimal amount) {
        List<GroupMember> members = groupMemberRepository.findByGroupId(groupId);
        if (members.isEmpty()) {
            throw new BadRequestException("Group has no members to split expense among");
        }

        int n = members.size();
        BigDecimal baseShare = amount.divide(BigDecimal.valueOf(n), 2, RoundingMode.FLOOR);
        BigDecimal distributed = baseShare.multiply(BigDecimal.valueOf(n));
        BigDecimal remainder = amount.subtract(distributed); // goes to the payer

        List<ExpenseSplit> splits = new ArrayList<>();
        for (GroupMember member : members) {
            boolean isPayer = member.getUser().getId().equals(expense.getPaidBy().getId());
            BigDecimal share = isPayer ? baseShare.add(remainder) : baseShare;
            splits.add(ExpenseSplit.builder()
                    .expense(expense)
                    .user(member.getUser())
                    .shareAmount(share)
                    .build());
        }
        return splits;
    }

    /**
     * Splits the amount using explicit per-user amounts provided by the client.
     * Validates: every user is a group member, and shares sum exactly to `amount`.
     */
    private List<ExpenseSplit> buildExactSplits(Expense expense, Long groupId, BigDecimal amount, List<SplitInput> splitInputs) {
        if (splitInputs == null || splitInputs.isEmpty()) {
            throw new BadRequestException("Provide 'splits' or set 'splitEqually': true");
        }

        BigDecimal sum = BigDecimal.ZERO;
        List<ExpenseSplit> splits = new ArrayList<>();

        for (SplitInput input : splitInputs) {
            if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, input.getUserId())) {
                throw new BadRequestException("User " + input.getUserId() + " is not a member of group " + groupId);
            }
            User user = userService.findUserOrThrow(input.getUserId());
            sum = sum.add(input.getShareAmount());
            splits.add(ExpenseSplit.builder()
                    .expense(expense)
                    .user(user)
                    .shareAmount(input.getShareAmount())
                    .build());
        }

        if (sum.compareTo(amount) != 0) {
            throw new BadRequestException(
                    "Split amounts (" + sum + ") must sum exactly to the expense amount (" + amount + ")");
        }
        return splits;
    }

    @Transactional(readOnly = true)
    public ExpenseResponse getExpense(Long id) {
        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found with id: " + id));
        return toResponse(expense);
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> getExpensesForGroup(Long groupId) {
        groupService.findGroupOrThrow(groupId);
        return expenseRepository.findByGroupId(groupId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public void deleteExpense(Long id) {
        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found with id: " + id));
        expenseRepository.delete(expense);
    }

    private ExpenseResponse toResponse(Expense expense) {
        List<SplitResponse> splits = expense.getSplits().stream()
                .map(s -> SplitResponse.builder()
                        .userId(s.getUser().getId())
                        .userName(s.getUser().getName())
                        .shareAmount(s.getShareAmount())
                        .build())
                .toList();

        return ExpenseResponse.builder()
                .id(expense.getId())
                .groupId(expense.getGroup().getId())
                .paidByUserId(expense.getPaidBy().getId())
                .paidByUserName(expense.getPaidBy().getName())
                .description(expense.getDescription())
                .amount(expense.getAmount())
                .createdAt(expense.getCreatedAt())
                .splits(splits)
                .build();
    }
}
