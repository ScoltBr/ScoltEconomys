package me.scoltbr.scoltEconomys.stock;

/** Thin wrapper que o scheduler chama para acionar o tick de oscilação de preços. */
public final class StockPriceTicker {

    private final StockMarketService service;

    public StockPriceTicker(StockMarketService service) {
        this.service = service;
    }

    public void tick() {
        service.tick();
    }
}
