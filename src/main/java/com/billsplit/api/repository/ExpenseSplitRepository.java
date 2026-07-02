package com.billsplit.api.repository;

import com.billsplit.api.dto.UserBalanceProjection;
import com.billsplit.api.entity.ExpenseSplit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExpenseSplitRepository extends JpaRepository<ExpenseSplit, Long> {

    List<ExpenseSplit> findByExpenseId(Long expenseId);

    /**
     * Core balance-calculation query.
     *
     * For every user in the group:
     *   totalPaid  = sum of expense.amount for expenses they paid for
     *   totalOwed  = sum of expense_splits.share_amount allocated to them
     *   netBalance = totalPaid - totalOwed
     *
     * netBalance > 0  -> the group owes this user money
     * netBalance < 0  -> this user owes the group money
     *
     * Implemented as a native query using LEFT JOINs + GROUP BY so both
     * "amount paid" and "amount owed" are aggregated in a single pass,
     * even for users who paid for nothing or aren't part of any split yet.
     */
    @Query(value = """
            SELECT
                u.id                                    AS userId,
                u.name                                  AS userName,
                COALESCE(paid.total_paid, 0)             AS totalPaid,
                COALESCE(owed.total_owed, 0)             AS totalOwed,
                COALESCE(paid.total_paid, 0) - COALESCE(owed.total_owed, 0) AS netBalance
            FROM group_members gm
            JOIN users u ON u.id = gm.user_id
            LEFT JOIN (
                SELECT e.paid_by_user_id AS user_id, SUM(e.amount) AS total_paid
                FROM expenses e
                WHERE e.group_id = :groupId
                GROUP BY e.paid_by_user_id
            ) paid ON paid.user_id = u.id
            LEFT JOIN (
                SELECT es.user_id AS user_id, SUM(es.share_amount) AS total_owed
                FROM expense_splits es
                JOIN expenses e2 ON e2.id = es.expense_id
                WHERE e2.group_id = :groupId
                GROUP BY es.user_id
            ) owed ON owed.user_id = u.id
            WHERE gm.group_id = :groupId
            ORDER BY netBalance DESC
            """, nativeQuery = true)
    List<UserBalanceProjection> calculateGroupBalances(@Param("groupId") Long groupId);
}
