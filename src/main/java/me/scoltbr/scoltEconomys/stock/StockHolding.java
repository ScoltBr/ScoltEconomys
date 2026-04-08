package me.scoltbr.scoltEconomys.stock;

import java.util.UUID;

/** Posição de um jogador em uma determinada ação. */
public record StockHolding(UUID uuid, String stockId, long quantity, double avgPrice) {

    /** Retorna nova holding com a quantidade e preço médio atualizados após compra. */
    public StockHolding add(long qty, double purchasePrice) {
        long newQty = quantity + qty;
        double newAvg = newQty == 0 ? purchasePrice
                : ((avgPrice * quantity) + (purchasePrice * qty)) / newQty;
        return new StockHolding(uuid, stockId, newQty, newAvg);
    }

    /** Retorna nova holding com a quantidade reduzida após venda. */
    public StockHolding remove(long qty) {
        return new StockHolding(uuid, stockId, quantity - qty, avgPrice);
    }

    /** P&L não realizado (positivo = lucro, negativo = prejuízo). */
    public double unrealizedPnl(double currentPrice) {
        return (currentPrice - avgPrice) * quantity;
    }

    /** Variação percentual em relação ao preço médio de compra. */
    public double pnlPercent(double currentPrice) {
        if (avgPrice <= 0) return 0.0;
        return ((currentPrice - avgPrice) / avgPrice) * 100.0;
    }
}
