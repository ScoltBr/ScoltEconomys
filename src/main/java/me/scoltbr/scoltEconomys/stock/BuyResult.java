package me.scoltbr.scoltEconomys.stock;

/** Resultado de uma operação de compra de ações. */
public record BuyResult(boolean success, long qty, double totalPaid, double fee, double pricePerShare, String reason) {

    public static BuyResult ok(long qty, double totalPaid, double fee, double pricePerShare) {
        return new BuyResult(true, qty, totalPaid, fee, pricePerShare, null);
    }

    public static BuyResult fail(String reason) {
        return new BuyResult(false, 0, 0, 0, 0, reason);
    }
}
