package me.scoltbr.scoltEconomys.bank;

import me.scoltbr.scoltEconomys.account.AccountCache;
import me.scoltbr.scoltEconomys.account.AccountService;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

public final class BankInterestService {

    private final Plugin plugin;
    private final AccountCache cache;
    private final AccountService accounts;
    private final me.scoltbr.scoltEconomys.event.EventManager eventManager;

    public BankInterestService(Plugin plugin, AccountCache cache, AccountService accounts, me.scoltbr.scoltEconomys.event.EventManager eventManager) {
        this.plugin = plugin;
        this.cache = cache;
        this.accounts = accounts;
        this.eventManager = eventManager;
    }

    public void tick() {
        boolean enabled = plugin.getConfig().getBoolean("bank.interest.enabled", false);
        if (!enabled) return;

        double rate = plugin.getConfig().getDouble("bank.interest.rate", 0.0);
        
        // Aplica multiplicador do evento ativo
        rate *= eventManager.getInterestMultiplier();
        
        double cap = plugin.getConfig().getDouble("bank.interest.cap-per-interval", 0.0);

        boolean onlineOnly = plugin.getConfig().getBoolean("bank.interest.online-only", true);

        // Itera apenas contas em cache (online + carregadas)
        for (java.util.UUID uuid : cache.cachedUuids()) {
            if (onlineOnly && org.bukkit.Bukkit.getPlayer(uuid) == null) continue;
            
            accounts.applyBankInterest(uuid, rate, cap);
        }
    }
}
