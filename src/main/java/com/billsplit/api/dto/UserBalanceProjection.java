package com.billsplit.api.dto;

import java.math.BigDecimal;

/**
 * Spring Data JPA interface-based projection.
 * Column aliases in the native query (userId, userName, totalPaid, totalOwed, netBalance)
 * are mapped automatically to these getter names.
 */
public interface UserBalanceProjection {
    Long getUserId();
    String getUserName();
    BigDecimal getTotalPaid();
    BigDecimal getTotalOwed();
    BigDecimal getNetBalance();
}
