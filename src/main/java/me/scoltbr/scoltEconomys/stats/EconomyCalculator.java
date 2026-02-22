package me.scoltbr.scoltEconomys.stats;

import me.scoltbr.scoltEconomys.account.AccountCache;
import me.scoltbr.scoltEconomys.account.PlayerAccount;
import org.bukkit.Bukkit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class EconomyCalculator {

    private final AccountCache cache;

    public EconomyCalculator(AccountCache cache) {
        this.cache = cache;
    }

    public EconomySnapshot calculate() {

        List<PlayerAccount> accounts = new ArrayList<>(cache.allAccounts());

        double totalWallet = accounts.stream().mapToDouble(PlayerAccount::wallet).sum();
        double totalBank = accounts.stream().mapToDouble(PlayerAccount::bank).sum();
        double total = totalWallet + totalBank;

        int activePlayers = Bukkit.getOnlinePlayers().size();
        double avgPerActive = activePlayers == 0 ? 0.0 : total / activePlayers;

        accounts.sort(Comparator.comparingDouble(a -> a.wallet() + a.bank()));
        int n = accounts.size();
        int topCount = Math.max(1, n / 10);

        double topTotal = accounts.stream()
                .skip(Math.max(0, n - topCount))
                .mapToDouble(a -> a.wallet() + a.bank())
                .sum();

        double concentration = total <= 0 ? 0.0 : (topTotal / total);

        return new EconomySnapshot(
                Instant.now(),
                total,
                totalWallet,
                totalBank,
                activePlayers,
                avgPerActive,
                concentration
        );
    }
}