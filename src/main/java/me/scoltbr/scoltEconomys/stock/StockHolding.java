package me.scoltbr.scoltEconomys.stock;

import java.util.UUID;

/** Posição de um jogador em uma determinada ação. */
public record StockHolding(UUID uuid, String stockId, long quantity, java.math.BigDecimal avgPrice) {

    /** Retorna nova holding com a quantidade e preço médio atualizados após compra. */
    public StockHolding add(long qty, java.math.BigDecimal purchasePrice) {
        long newQty = quantity + qty;
        java.math.BigDecimal newAvg = newQty == 0 ? purchasePrice
                : avgPrice.multiply(java.math.BigDecimal.valueOf(quantity))
                    .add(purchasePrice.multiply(java.math.BigDecimal.valueOf(qty)))
                    .divide(java.math.BigDecimal.valueOf(newQty), 4, java.math.RoundingMode.HALF_UP);
        return new StockHolding(uuid, stockId, newQty, newAvg);
    }

    /** Retorna nova holding com a quantidade reduzida após venda. */
    public StockHolding remove(long qty) {
        return new StockHolding(uuid, stockId, quantity - qty, avgPrice);
    }

    /** P&L não realizado (positivo = lucro, negativo = prejuízo). */
    public java.math.BigDecimal unrealizedPnl(java.math.BigDecimal currentPrice) {
        return currentPrice.subtract(avgPrice).multiply(java.math.BigDecimal.valueOf(quantity));
    }

    /** Variação percentual em relação ao preço médio de compra. */
    public double pnlPercent(java.math.BigDecimal currentPrice) {
        if (avgPrice.compareTo(java.math.BigDecimal.ZERO) <= 0) return 0.0;
        return currentPrice.subtract(avgPrice).divide(avgPrice, 4, java.math.RoundingMode.HALF_UP).doubleValue() * 100.0;
    }
}
