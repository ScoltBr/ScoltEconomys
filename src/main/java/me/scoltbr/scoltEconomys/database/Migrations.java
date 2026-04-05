package me.scoltbr.scoltEconomys.database;

import org.bukkit.plugin.Plugin;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

public final class Migrations {

    private Migrations() {}

    public static void run(Plugin plugin, DataSource ds) {
        try (Connection c = ds.getConnection();
             Statement st = c.createStatement()) {

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS se_accounts (
                    uuid           CHAR(36) PRIMARY KEY,
                    wallet_balance DOUBLE NOT NULL,
                    bank_balance   DOUBLE NOT NULL,
                    last_update    BIGINT NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

            st.executeUpdate("""
                 CREATE TABLE IF NOT EXISTS se_economy_daily (
                 day               DATE PRIMARY KEY,
                 total_coins       DOUBLE NOT NULL,
                 total_wallet      DOUBLE NOT NULL,
                 total_bank        DOUBLE NOT NULL,
                 active_players    INT NOT NULL,
                 top_concentration DOUBLE NOT NULL,
                 updated_at        BIGINT NOT NULL
             ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS se_treasury (
                    id      TINYINT PRIMARY KEY DEFAULT 1,
                    balance DOUBLE NOT NULL DEFAULT 0.0
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

            // Garante que a linha única sempre existe
            st.executeUpdate("INSERT IGNORE INTO se_treasury (id, balance) VALUES (1, 0.0)");

        } catch (Exception e) {
            plugin.getLogger().severe("Failed running migrations: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}