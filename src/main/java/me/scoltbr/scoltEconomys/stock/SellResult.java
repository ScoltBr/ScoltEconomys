package me.scoltbr.scoltEconomys.stock;

/** Resultado de uma operação de venda de ações. */
public record SellResult(boolean success, long qty, double net, double fee, double profit, double pricePerShare, String reason) {

    public static SellResult ok(long qty, double net, double fee, double profit, double pricePerShare) {
        return new SellResult(true, qty, net, fee, profit, pricePerShare, null);
    }

    public static SellResult fail(String reason) {
        return new SellResult(false, 0, 0, 0, 0, 0, reason);
    }
}
