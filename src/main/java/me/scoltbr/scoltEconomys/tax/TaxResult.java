package me.scoltbr.scoltEconomys.tax;

import java.math.BigDecimal;

public record TaxResult(BigDecimal netAmount, BigDecimal feeAmount) {
    public TaxResult {
        if (netAmount.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("netAmount must be >= 0");
        if (feeAmount.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("feeAmount must be >= 0");
    }
}