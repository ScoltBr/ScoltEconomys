package me.scoltbr.scoltEconomys.api.event;

/**
 * Evento disparado quando o preço de uma ação oscila durante o processamento do ticker.
 * <p>
 * Este evento ocorre na Main Thread e serve para plugins de monitoramento de mercado
 * ou bots de trade reagirem às mudanças de preço.
 * </p>
 */
public final class StockPriceUpdateEvent extends ScoltEconomyEvent {

    private final String stockId;
    private final java.math.BigDecimal oldPrice;
    private final java.math.BigDecimal newPrice;

    public StockPriceUpdateEvent(String stockId, java.math.BigDecimal oldPrice, java.math.BigDecimal newPrice) {
        super(false); // Sync
        this.stockId = stockId;
        this.oldPrice = oldPrice;
        this.newPrice = newPrice;
    }

    /** @return ID da empresa que oscilou. */
    public String getStockId() { return stockId; }

    /** @return preço antes da oscilação. */
    public java.math.BigDecimal getOldPrice() { return oldPrice; }

    /** @return novo preço pós-tick. */
    public java.math.BigDecimal getNewPrice() { return newPrice; }

    /** @return variação nominal (new - old). */
    public java.math.BigDecimal getChange() { return newPrice.subtract(oldPrice); }

    /** @return variação percentual (ex: 2.5 para +2.5%). */
    public double getChangePercent() {
        if (oldPrice.compareTo(java.math.BigDecimal.ZERO) <= 0) return 0;
        return newPrice.subtract(oldPrice).divide(oldPrice, 4, java.math.RoundingMode.HALF_UP).doubleValue() * 100.0;
    }
}
