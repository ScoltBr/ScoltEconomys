package me.scoltbr.scoltEconomys.stats;

import java.time.Instant;

public record EconomySnapshot(
        Instant at,
        double totalCoins,
        double totalWallet,
        double totalBank,
        int activePlayers,
        double averagePerActivePlayer,
        double top10Concentration
) {}