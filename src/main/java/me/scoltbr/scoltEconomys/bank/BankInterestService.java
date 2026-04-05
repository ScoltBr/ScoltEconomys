package me.scoltbr.scoltEconomys.bank;

import me.scoltbr.scoltEconomys.account.AccountCache;
import me.scoltbr.scoltEconomys.account.AccountService;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

public final class BankInterestService {

    private final Plugin plugin;
    private final AccountCache cache;
    private final AccountService accounts;

    public BankInterestService(Plugin plugin, AccountCache cache, AccountService accounts) {
        this.plugin = plugin;
        this.cache = cache;
        this.accounts = accounts;
    }

    public void tick() {
        boolean enabled = plugin.getConfig().getBoolean("bank.interest.enabled", false);
        if (!enabled) return;

        double rate = plugin.getConfig().getDouble("bank.interest.rate", 0.0);
        double cap = plugin.getConfig().getDouble("bank.interest.cap-per-interval", 0.0);

        boolean onlineOnly = plugin.getConfig().getBoolean("bank.interest.online-only", true);

        // Itera apenas contas em cache (online + carregadas)
        for (UUID uuid : cache.cachedUuids()) {
            if (onlineOnly && org.bukkit.Bukkit.getPlayer(uuid) == null) continue;
            
            accounts.applyBankInterest(uuid, rate, cap);
        }
    }
}
