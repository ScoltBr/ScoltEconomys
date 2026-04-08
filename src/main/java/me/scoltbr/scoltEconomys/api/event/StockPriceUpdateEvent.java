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
    private final double oldPrice;
    private final double newPrice;

    public StockPriceUpdateEvent(String stockId, double oldPrice, double newPrice) {
        super(false); // Sync
        this.stockId = stockId;
        this.oldPrice = oldPrice;
        this.newPrice = newPrice;
    }

    /** @return ID da empresa que oscilou. */
    public String getStockId() { return stockId; }

    /** @return preço antes da oscilação. */
    public double getOldPrice() { return oldPrice; }

    /** @return novo preço pós-tick. */
    public double getNewPrice() { return newPrice; }

    /** @return variação nominal (new - old). */
    public double getChange() { return newPrice - oldPrice; }

    /** @return variação percentual (ex: 2.5 para +2.5%). */
    public double getChangePercent() {
        if (oldPrice <= 0) return 0;
        return ((newPrice - oldPrice) / oldPrice) * 100.0;
    }
}
