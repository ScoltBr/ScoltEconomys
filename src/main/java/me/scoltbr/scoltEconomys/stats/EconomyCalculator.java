package me.scoltbr.scoltEconomys.stats;

import me.scoltbr.scoltEconomys.account.AccountRepository;
import org.bukkit.Bukkit;

import java.time.Instant;

public final class EconomyCalculator {

    private final AccountRepository repo;

    public EconomyCalculator(AccountRepository repo) {
        this.repo = repo;
    }

    public EconomySnapshot calculate() {

        var data = repo.getGlobalEconomyData();

        double total = data.totalWallet() + data.totalBank();

        int activePlayers = Bukkit.getOnlinePlayers().size();
        double avgPerActive = activePlayers == 0 ? 0.0 : total / activePlayers;

        double concentration = total <= 0 ? 0.0 : (data.top10Wealth() / total);

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