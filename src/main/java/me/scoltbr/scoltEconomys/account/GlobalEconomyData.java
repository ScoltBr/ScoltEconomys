package me.scoltbr.scoltEconomys.account;

import java.math.BigDecimal;

public record GlobalEconomyData(
    BigDecimal totalWallet,
    BigDecimal totalBank,
    int totalAccounts,
    BigDecimal top10Wealth
) {}
