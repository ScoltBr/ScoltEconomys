package me.scoltbr.scoltEconomys.alerts;

import me.scoltbr.scoltEconomys.stats.AdminStatsService;
import me.scoltbr.scoltEconomys.stats.EconomySnapshot;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.util.*;

public final class AlertService {

    private final Plugin plugin;
    private final AdminStatsService stats;

    private final Map<AlertType, Alert> active = Collections.synchronizedMap(new EnumMap<>(AlertType.class));

    public AlertService(Plugin plugin, AdminStatsService stats) {
        this.plugin = plugin;
        this.stats = stats;
    }

    public void tick() {
        if (!plugin.getConfig().getBoolean("alerts.enabled", true)) return;

        EconomySnapshot snap = stats.calculateNow();
        double minTotal = plugin.getConfig().getDouble("alerts.min-total-coins", 0.0);

        // Se economia ainda é muito pequena, limpa alertas e sai
        if (snap.totalCoins() < minTotal) {
            active.clear();
            return;
        }

        double growthLimit = plugin.getConfig().getDouble("alerts.growth-high", 0.15);
        double concentrationLimit = plugin.getConfig().getDouble("alerts.concentration-high", 0.70);

        // 1) crescimento 24h
        stats.growth24h().ifPresentOrElse(growth -> {
            if (growth >= growthLimit) {
                upsert(AlertType.HIGH_GROWTH_24H,
                        "Crescimento 24h alto: " + pct(growth) + " (limite " + pct(growthLimit) + ")");
            } else {
                active.remove(AlertType.HIGH_GROWTH_24H);
            }
        }, () -> active.remove(AlertType.HIGH_GROWTH_24H));

        // 2) concentração top 10%
        if (snap.top10Concentration() >= concentrationLimit) {
            upsert(AlertType.HIGH_CONCENTRATION_TOP10,
                    "Concentração Top 10% alta: " + pct(snap.top10Concentration()) +
                            " (limite " + pct(concentrationLimit) + ")");
        } else {
            active.remove(AlertType.HIGH_CONCENTRATION_TOP10);
        }
    }

    public List<Alert> activeAlerts() {
        return List.copyOf(active.values());
    }

    private void upsert(AlertType type, String message) {
        active.compute(type, (k, existing) -> {
            if (existing == null) return new Alert(type, message, Instant.now());
            return new Alert(type, message, existing.since());
        });
    }

    private String pct(double v) {
        return String.format(Locale.US, "%.2f%%", v * 100.0);
    }
}
