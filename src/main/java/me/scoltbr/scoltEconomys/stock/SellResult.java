package me.scoltbr.scoltEconomys.stock;

/** Resultado de uma operação de venda de ações. */
public record SellResult(boolean success, long qty, java.math.BigDecimal net, java.math.BigDecimal fee, java.math.BigDecimal profit, java.math.BigDecimal pricePerShare, String reason) {

    public static SellResult ok(long qty, java.math.BigDecimal net, java.math.BigDecimal fee, java.math.BigDecimal profit, java.math.BigDecimal pricePerShare) {
        return new SellResult(true, qty, net, fee, profit, pricePerShare, null);
    }

    public static SellResult fail(String reason) {
        return new SellResult(false, 0, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, reason);
    }
}
