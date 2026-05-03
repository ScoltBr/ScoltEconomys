package me.scoltbr.scoltEconomys.commodity;

import java.math.BigDecimal;

/** Snapshot de preço de uma commodity em um instante. */
public record CommodityPrice(String commodityId, BigDecimal price, long recordedAt) {}
