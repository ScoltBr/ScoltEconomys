package me.scoltbr.scoltEconomys.stats;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EconomyDailyRow(
        LocalDate day,
        BigDecimal totalCoins,
        BigDecimal totalWallet,
        BigDecimal totalBank,
        int activePlayers,
        BigDecimal top10Concentration,
        long updatedAt
) {}
