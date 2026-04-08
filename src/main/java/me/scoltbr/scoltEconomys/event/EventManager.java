package me.scoltbr.scoltEconomys.event;

import me.scoltbr.scoltEconomys.util.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class EventManager {

    private final Plugin plugin;
    private final Map<String, EconomyEvent> definitions = new HashMap<>();

    private EconomyEvent activeEvent = null;
    private long endTime = 0;
    private BukkitTask expirationTask = null;

    public EventManager(Plugin plugin) {
        this.plugin = plugin;
        loadDefinitions();
    }

    public void loadDefinitions() {
        definitions.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("events.definitions");
        if (section == null)
            return;

        for (String key : section.getKeys(false)) {
            ConfigurationSection eventSection = section.getConfigurationSection(key);
            if (eventSection == null)
                continue;

            String name = eventSection.getString("name", key);
            double intMul = eventSection.getDouble("interest-multiplier", 1.0);
            double taxMul = eventSection.getDouble("tax-multiplier", 1.0);
            long duration = eventSection.getLong("duration", 3600);

            // Load per-sector stock price boosts
            Map<String, Double> boosts = new HashMap<>();
            ConfigurationSection boostSection = eventSection.getConfigurationSection("sector-boosts");
            if (boostSection != null) {
                for (String sector : boostSection.getKeys(false)) {
                    boosts.put(sector, boostSection.getDouble(sector, 0.0));
                }
            }

            definitions.put(key, new EconomyEvent(key, name, intMul, taxMul, duration, Collections.unmodifiableMap(boosts)));
        }
    }

    public boolean startEvent(String id) {
        EconomyEvent event = definitions.get(id);
        if (event == null)
            return false;

        stopEvent(); // Para o atual se houver

        this.activeEvent = event;
        this.endTime = System.currentTimeMillis() + (event.durationSeconds() * 1000);

        // Broadcast de início
        MessageUtils.broadcast("");
        MessageUtils.broadcast("  <gold><bold>EVENTO ECONÔMICO INICIADO!</bold></gold>");
        MessageUtils.broadcast("  <white>Evento:</white> " + event.displayName());
        MessageUtils
                .broadcast("  <white>Duração:</white> <yellow>" + (event.durationSeconds() / 60) + " minutos</yellow>");
        MessageUtils.broadcast("");

        // Agendar encerramento
        expirationTask = Bukkit.getScheduler().runTaskLater(plugin, this::stopEvent, event.durationSeconds() * 20L);
        return true;
    }

    public void stopEvent() {
        if (activeEvent == null)
            return;

        MessageUtils.broadcast("");
        MessageUtils.broadcast("  <red><bold>EVENTO ECONÔMICO ENCERRADO:</bold></red> " + activeEvent.displayName());
        MessageUtils.broadcast("");

        this.activeEvent = null;
        this.endTime = 0;
        if (expirationTask != null) {
            expirationTask.cancel();
            expirationTask = null;
        }
    }

    public Optional<EconomyEvent> getActiveEvent() {
        return Optional.ofNullable(activeEvent);
    }

    public double getInterestMultiplier() {
        return activeEvent != null ? activeEvent.interestMultiplier() : 1.0;
    }

    public double getTaxMultiplier() {
        return activeEvent != null ? activeEvent.taxMultiplier() : 1.0;
    }

    public long getRemainingSeconds() {
        if (activeEvent == null)
            return 0;
        long remaining = (endTime - System.currentTimeMillis()) / 1000;
        return Math.max(0, remaining);
    }

    public Map<String, EconomyEvent> getDefinitions() {
        return new HashMap<>(definitions);
    }

    /**
     * Returns the stock price boost modifier for a given sector during the active event.
     * Returns 0.0 if no event is active or the sector has no defined boost.
     */
    public double getSectorBoost(String sector) {
        if (activeEvent == null) return 0.0;
        return activeEvent.sectorBoosts().getOrDefault(sector, 0.0);
    }

    public void checkAutoEvent() {
        if (activeEvent != null)
            return; // Já há um evento ativo
        if (!plugin.getConfig().getBoolean("events.auto.enabled", true))
            return;

        double prob = plugin.getConfig().getDouble("events.auto.probability", 0.3);
        if (Math.random() > prob)
            return;

        if (definitions.isEmpty())
            return;

        // Seleciona um ID aleatório das definições
        Object[] keys = definitions.keySet().toArray();
        String randomId = (String) keys[new java.util.Random().nextInt(keys.length)];

        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.getLogger().info("[ScoltEconomys] Iniciando evento automático: " + randomId);
            startEvent(randomId);
        });
    }

    public void tickActionBar() {
        if (activeEvent == null)
            return;

        String rawMessage = activeEvent.displayName() + " <gray>| Faltam: <aqua>" + formatTime(getRemainingSeconds()) + "</aqua></gray>";

        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            MessageUtils.actionBar(player, rawMessage);
        }
    }

    private String formatTime(long seconds) {
        if (seconds < 60)
            return seconds + "s";
        long mins = seconds / 60;
        long secs = seconds % 60;
        return mins + "m " + secs + "s";
    }
}
