package me.scoltbr.scoltEconomys.tax;

public record TaxResult(double netAmount, double feeAmount) {
    public TaxResult {
        if (netAmount < 0) throw new IllegalArgumentException("netAmount must be >= 0");
        if (feeAmount < 0) throw new IllegalArgumentException("feeAmount must be >= 0");
    }
}