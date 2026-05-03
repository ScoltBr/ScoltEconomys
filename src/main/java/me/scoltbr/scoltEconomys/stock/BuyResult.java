package me.scoltbr.scoltEconomys.stock;

/** Resultado de uma operação de compra de ações. */
public record BuyResult(boolean success, long qty, java.math.BigDecimal totalPaid, java.math.BigDecimal fee, java.math.BigDecimal pricePerShare, String reason) {

    public static BuyResult ok(long qty, java.math.BigDecimal totalPaid, java.math.BigDecimal fee, java.math.BigDecimal pricePerShare) {
        return new BuyResult(true, qty, totalPaid, fee, pricePerShare, null);
    }

    public static BuyResult fail(String reason) {
        return new BuyResult(false, 0, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, reason);
    }
}
