package me.scoltbr.scoltEconomys.stock;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface StockRepository {

    void savePrice(StockPrice price);

    List<StockPrice> getHistory(String stockId, int limit);

    Optional<StockHolding> getHolding(UUID uuid, String stockId);

    Map<String, StockHolding> getAllHoldings(UUID uuid);

    void upsertHolding(StockHolding holding);

    void deleteHolding(UUID uuid, String stockId);

    void recordTransaction(UUID uuid, String stockId, String type, long qty, double price, double total);

    List<StockHolding> getTopHolders(String stockId, int limit);

    long sumHeldShares(String stockId);

    /** Remove entradas antigas de preço, mantendo apenas os últimos {@code keep} registros por empresa. */
    void purgeOldPrices(String stockId, int keep);
}

