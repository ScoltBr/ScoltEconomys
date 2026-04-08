package me.scoltbr.scoltEconomys.stock;

/** Snapshot de preço de uma ação em um dado momento. */
public record StockPrice(String stockId, double price, long recordedAt) {}
