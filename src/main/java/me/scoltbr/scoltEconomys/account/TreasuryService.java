package me.scoltbr.scoltEconomys.account;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

public final class TreasuryService {

    private final boolean enabled;
    private double treasuryWallet;

    public TreasuryService(Plugin plugin) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("treasury");
        this.enabled = section != null && section.getBoolean("enabled", true);
        this.treasuryWallet = 0.0;
    }

    public void collect(double amount) {
        if (!enabled) return;
        if (amount <= 0) return;
        treasuryWallet += amount;
    }

    public double balance() {
        return treasuryWallet;
    }
}
