package me.scoltbr.scoltEconomys.stats;

import me.scoltbr.scoltEconomys.alerts.AlertService;
import org.bukkit.plugin.Plugin;

public final class StatsTickService {

    private final Plugin plugin;
    private final AdminStatsService stats;
    private final AlertService alerts;

    public StatsTickService(Plugin plugin, AdminStatsService stats, AlertService alerts) {
        this.plugin = plugin;
        this.stats = stats;
        this.alerts = alerts;
    }

    public void tick() {
        if (!plugin.getConfig().getBoolean("stats.enabled", true)) return;

        EconomySnapshot snap = stats.calculateNow();
        stats.persistToday(snap);

        alerts.tick();
    }
}