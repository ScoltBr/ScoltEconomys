package me.scoltbr.scoltEconomys.account;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class TreasuryService {

    private final Plugin plugin;
    private final DataSource ds;
    private final boolean enabled;
    private double treasuryWallet;
    private boolean dirty;
    private int taskId;

    public TreasuryService(Plugin plugin, DataSource ds) {
        this.plugin = plugin;
        this.ds = ds;
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("treasury");
        this.enabled = section != null && section.getBoolean("enabled", true);
        this.treasuryWallet = 0.0;
        this.dirty = false;
        this.taskId = -1;
    }

    public void start() {
        if (!enabled)
            return;
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement("SELECT balance FROM se_treasury WHERE id = 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    this.treasuryWallet = rs.getDouble("balance");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load treasury balance: " + e.getMessage());
        }

        taskId = org.bukkit.Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flush, 200L, 200L)
                .getTaskId();
    }

    public synchronized void flush() {
        if (!enabled || !dirty)
            return;

        double currentBalance = treasuryWallet;
        dirty = false;

        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement("UPDATE se_treasury SET balance = ? WHERE id = 1")) {
            ps.setDouble(1, currentBalance);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to save treasury balance: " + e.getMessage());
        }
    }

    public synchronized void stop() {
        if (taskId != -1) {
            org.bukkit.Bukkit.getScheduler().cancelTask(taskId);
        }
        flush();
    }

    public synchronized void collect(double amount) {
        if (!enabled || amount <= 0)
            return;
        treasuryWallet += amount;
        dirty = true;
    }

    public synchronized double balance() {
        return treasuryWallet;
    }
}