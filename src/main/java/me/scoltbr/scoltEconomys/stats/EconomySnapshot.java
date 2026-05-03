package me.scoltbr.scoltEconomys.stats;

import java.time.Instant;
import java.math.BigDecimal;

public record EconomySnapshot(
        Instant at,
        BigDecimal totalCoins,
        BigDecimal totalWallet,
        BigDecimal totalBank,
        int activePlayers,
        BigDecimal averagePerActivePlayer,
        BigDecimal top10Concentration
) {}