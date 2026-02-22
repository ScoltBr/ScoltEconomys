package me.scoltbr.scoltEconomys.stats;

import java.time.LocalDate;

public record EconomyDailyRow(
        LocalDate day,
        double totalCoins,
        double totalWallet,
        double totalBank,
        int activePlayers,
        double top10Concentration,
        long updatedAt
) {}
