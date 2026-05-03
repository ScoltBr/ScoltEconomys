package me.scoltbr.scoltEconomys.stats;

import me.scoltbr.scoltEconomys.account.AccountRepository;
import org.bukkit.Bukkit;

import java.time.Instant;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class EconomyCalculator {

    private final AccountRepository repo;

    public EconomyCalculator(AccountRepository repo) {
        this.repo = repo;
    }

    public EconomySnapshot calculate() {

        var data = repo.getGlobalEconomyData();

        BigDecimal total = data.totalWallet().add(data.totalBank());

        int activePlayers = Bukkit.getOnlinePlayers().size();
        BigDecimal avgPerActive = activePlayers == 0 ? BigDecimal.ZERO : total.divide(BigDecimal.valueOf(activePlayers), 2, RoundingMode.HALF_UP);

        BigDecimal concentration = total.compareTo(BigDecimal.ZERO) <= 0
                ? BigDecimal.ZERO
                : data.top10Wealth().divide(total, 4, RoundingMode.HALF_UP);

        return new EconomySnapshot(
                Instant.now(),
                total,
                data.totalWallet(),
                data.totalBank(),
                activePlayers,
                avgPerActive,
                concentration
        );
    }
}